#!/usr/bin/env groovy

/*
  conditionalRetry
  
  A custom step to do conditional retry within a stage in Jenkins Declarative Pipelines.
  It's useful to run long running pipelines with many stages on Preemptible/Spot infrastructure.

  Actually it's the only way to do conditional retry within a stage on preemptible/spot nodes
  since Jenkins doesn't support that yet.

  The idea of this custom step is simple, it's used to run other steps on a new node,
  if the steps failed, the conditional retry step will scan logs of the stage and retry according to some patterns.

  Flow:
    Jenkins controller -> Intermediate node -> Execution node(s)

  Glossary:
    - Intermediate node:
      It's a tiny agent runs for the entire job (runs only during job build). Its main goal is to hold the build state.
      It's responsable for holding the retry logic like retry count; thus it must work on a stable infrastructure
      like the controller. So if the execution node died, the intermediate node will spin a new execution agent.
    - Execution node:
      It's the agent where the actual code will be executed. It could work on preemptible/spot instances
      since the retry logic is already hold by the intermediate node.

  Dependencies:
    - pipeline-logparser/logparser@v3.0 (https://github.com/gdemengin/pipeline-logparser)

  Parameters:
    @param String agentLabel - DEFAULT: None, OPTIONAL: No
      A label to run Jenkins execution node. There where actual code executed.
    @param boolean suppressErrors - DEFAULT: true
      Ignore exit status of any code within this method and set stage status to failure. This option is useful
      to continue the pipeline to the end even there is a failed stage. This is simply like 'failFast'
      for parallel stages but for the whole pipeline.
    @param int retryCount - DEFAULT: 3
      Number of tries to make if one of failure patterns is matched.
    @param int retryDelay - DEFAULT: 60
      How many seconds to wait before starting the next retry.
    @param List stageNameFilterPatterns - DEFAULT: ["${STAGE_NAME}"]
      Set the name of the stage/branch in Jenkins pipeline to get the its logs. This is useful to work with Jenkins
      Declarative Matrix where all stages in the matrix has the same name so the name of the stage parent is used.
      WARNING: this name will be used as a regexp, so don't use special characters: [ ] \ / ^ $ . | ? * + ( ) { }
      Note, this has nothing to do with git branches, it's the Jenkins pipeline structure.
    @param boolean useBuiltinFailurePatterns - DEFAULT: True
      Use predefined failure patterns. e.g. like node disconnected errors and so on.
    @param Map customFailurePatterns - DEFAULT: Empty map
      Add extra patterns to match of failed steps.
    @param Closure runSteps - DEFAULT: None
      Any valid Jenkins steps could be wrapped to run on the execution node.
    @param Closure postSuccess - DEFAULT: None
      Any valid Jenkins steps could be wrapped to run post runSteps in case of success.
    @param Closure postFailure - DEFAULT: None
      Any valid Jenkins steps could be wrapped to run post runSteps in case of failure.
    @param Closure postAlways - DEFAULT: None
      Any valid Jenkins steps could be wrapped to run post runSteps in all cases.

  Example:
    Here is an example for a pipeline that runs this custom method:

    ```
    @Library(['camunda-community', 'logparser']) _

    pipeline {
        agent {
            node {
                // This is the intermediate node label. It should run on a stable infrastructure.
                label 'jenkins-job-runner'
            }
        }
        stages {
            stage('Set failure count') {
                steps {
                    script {
                        FAILURE_COUNT = 1
                    }
                }
            }
            stage('Conditional retry demo') {
                steps {
                    conditionalRetry([
                        // This is the execution node label. It could run on an unstable infrastructure (spot/preemptible).
                        agentLabel: 'spot-instance-node',
                        suppressErrors: false,
                        retryCount: 3,
                        retryDelay: 5,
                        useBuiltinFailurePatterns: false,
                        customFailurePatterns: ['test-pattern': '.*DummyFailurePattern.*'],
                        // Demonstrate flaky stage and retry if the pattern found in the logs.
                        runSteps: {
                            sh '''
                                echo "The runSteps mimics the steps inside the pipeline stage."
                            '''
                            // This will fail only on the first run and it will be skipped in the second run.
                            script {
                                if (FAILURE_COUNT == 1) {
                                    --FAILURE_COUNT
                                    error 'This is just an error with a DummyFailurePattern that will be matched!'
                                }
                            }
                        },
                        postSuccess: {
                            echo "This will be printed if the steps succeeded in the retry node/agent"
                        },
                        postFailure: {
                            echo "This will be printed if the steps failed in the retry node/agent"
                        },
                        postAlways: {
                            echo "This will be always printed after the steps in the retry node/agent"
                        }
                    ])
                }
            }
        }
    }
    ```
*/

import groovy.transform.Field
import java.util.regex.Matcher
import java.util.regex.Pattern

@Field String stepName = this.class.name

@Field String retryIdentifier = "[$stepName] Tries left "

@Field boolean isVerbose = false

String getRetryLogs(List stageNameFilterPatterns, int retryCount) {
  /*
    Get logs for spacified retry only.
  */

  // Read logs from current stage.
  stageLogsFull = logparser.getLogsWithBranchInfo(showStages: true, filter: [stageNameFilterPatterns])

  // The previous retires are still showing in the logs,
  // thus using the retry identifier, we can get logs for current retry only.
  retryIdentifierLine = retryIdentifier + retryCount

  // The Pattern.quote method is used to have litral string because split accepts also regex.
  return stageLogsFull.split(Pattern.quote(retryIdentifierLine)).last()
}

boolean failurePatternsMatcher(
  String textToMatch,
  boolean useBuiltinFailurePatterns = true,
  Map customFailurePatterns = [:]
) {

  // Key = failure reason; Value = The regex to identify it.
  final Map<String, String> builtinFailurePatterns = [
    'connection-reset'           :  '^.*:? Connection reset$',
    'timeout'                    : '.*Error: timeout of .* exceeded.*',
    'jnlp-client-disconnected-1' : '^.*Caused: java.io.IOException: Backing channel \'JNLP4-connect connection from .*\' is disconnected.$',
    'jnlp-client-disconnected-2' : '^.*Remote call on JNLP4-connect connection from .* failed. The channel is closing down or has closed down$',
    'jnlp-client-disconnected-3' : 'java.nio.channels.ClosedChannelException',
    'jnlp-client-disconnected-4' :'hudson.AbortException: missing workspace',
    'jnlp-request-aborted-1'     : '.*Caused: java.io.IOException: remote file operation failed.*JNLP4-connect connection from.*',
    'jnlp-request-aborted-2'     : '.*Caused: hudson.remoting.RequestAbortedException.*',
    'java-oom'                   : '.*java.lang.OutOfMemoryError.*',
    'git-clone-failure'          : '^.*ERROR: Error cloning remote repo \'origin\'$',
  ]

  failurePatterns = useBuiltinFailurePatterns ? builtinFailurePatterns + customFailurePatterns : customFailurePatterns

  failurePatternsRegex = /(?:${failurePatterns.values().join('|')})/

  matchedLines = []

  echo("Start matching failure patterns")

  // Match all patterns line by line of the text.
  // Matching line by line provides better performance than matching multiline.
  textToMatch.split('\n').each { textToMatchLine ->
    def matched = textToMatchLine =~ failurePatternsRegex
    assert matched instanceof Matcher
    if (matched) {
      matchedLines.add(textToMatchLine)
    }
  }

  echo("Finished matching failure patterns")
  matchedLinesFormated = matchedLines ? "\n\t- ${matchedLines.join('\n\t- ')}" : "There are no matched patterns"
  echo("Matched lines: ${matchedLinesFormated}")

  return matchedLines.asBoolean()
}

void rerunConditionalRetry(Map parameters = [:], error) {
  echo('Rerun conditional retry if there is matched failure pattern')

  retryLogs = getRetryLogs(parameters.stageNameFilterPatterns, parameters.retryCount + 1)
  echo('Current retry logs:\n' + retryLogs)

  if (
    failurePatternsMatcher(
      retryLogs,
      parameters.useBuiltinFailurePatterns,
      parameters.customFailurePatterns
    )
  ) {
    sleep parameters.retryDelay
    call(parameters)
  } else {
    echo('There are no failure patterns matched')
    if (error) {
      conditionalThrowError(parameters.suppressErrors, error)
    }
  }
}

void runPostMethodIfDefined(methodCode) {
  if (methodCode) {
    // Catch errors from post action to avoid overriding main action errors.
    def resultStatus = 'UNSTABLE'
    def catchErrorMessage = echo(
      "An error has been thrown in post action. Build and stage status changed to: '${resultStatus}'."
    )
    catchError(message: catchErrorMessage, buildResult: null, stageResult: resultStatus) {
      methodCode()
    }
  }
}

void conditionalThrowError(boolean isErrorSuppressed, error) {
  if (isErrorSuppressed) {
    catchError(stageResult: 'FAILURE') {
      echo('An error has been thrown but suppressed and stage status changed to: \'FAILURE\'')
      throw error
    }
  } else {
    throw error
  }
}

// 
// NOTE: We cannot pass the closure to the method
// because the global objects like "env" and "param" are not available within the closure.
// More details: https://issues.jenkins.io/browse/JENKINS-44928  
void call(Map inputParameters = [:]) {
  //
  // Method parameters.

  // Mandatory parameters.
  if (!inputParameters.agentLabel) {
    error echo(
      "The following parameter is required and must be specified: 'agentLabel' " +
      "e.g. $stepName agentLabel: 'jenkins-agent-name'"
    )
  }

  defaultParameters = [
    suppressErrors: true,
    retryCount: 3,
    retryDelay: 60,
    stageNameFilterPatterns: ["${STAGE_NAME}"],
    useBuiltinFailurePatterns: true,
    customFailurePatterns: [:]
  ]

  availableParameters = [
    'agentLabel',
    'suppressErrors',
    'retryCount',
    'retryDelay',
    'stageNameFilterPatterns',
    'useBuiltinFailurePatterns',
    'customFailurePatterns',
    'runSteps',
    'postSuccess',
    'postFailure',
    'postAlways'
  ]

  def parameters = defaultParameters + inputParameters

  parameters.keySet().each { parameter ->
    assert parameter in availableParameters,
    echo("Invalid parameter '$parameter'. Available parameter are: $availableParameters")
  }

  // Log the start of the retry. It's used as a marker to differentiate between retries in the stage logs.
  echo retryIdentifier + parameters.retryCount

  // Try and catch any error that comes from executed closure code.
  try {
    echo('Run try section on the intermediate node/agent')

    parameters.retryCount--

    // Allocates a node/agent to execute the closure code.
    // This agent is different from the agent that runs the actual pipeline. This way is used to handle all errors
    // that coming from executed closure code including when the agent is disconnected/died.
    node(parameters.agentLabel) {

      // Mimic post section in the declarative pipeline.
      // Those actions will run on the node that will execute the retry steps.
      try {
        echo('Run try section on the execution node/agent')
        if (env.BRANCH_NAME) {
          checkout scm
        }
        echo('Run steps on the execution node/agent')
        parameters.runSteps()
        echo('Run postSuccess section on the execution node/agent')
        runPostMethodIfDefined(parameters.postSuccess)
      }
      catch (error) {
        echo('Run postFailure section on the execution node/agent')
        echo('Caught error on execution node/agent: ' + error)
        runPostMethodIfDefined(parameters.postFailure)
        throw error
      }
      finally {
        echo('Run postAlways section on the execution node/agent')
        runPostMethodIfDefined(parameters.postAlways)
      }
    }

  // This catch used in case the agent disconnected/died.
  } catch (error) {
    echo('Run catch section on the intermediate node/agent')
    echo('Caught error on intermediate node/agent: ' + error)

    if (parameters.retryCount > 0) {
      rerunConditionalRetry(parameters, error)

    } else {
      // If specified, ignore exit status after the retry and set stage status to failure.
      // For more details check this method docs.
      conditionalThrowError(parameters.suppressErrors, error)
    }
  } finally {
    // NOTE: This section is only for debugging purposes, no actual need for it.
    // It could be added to the 'post.always' section in the declarative pipeline.

    // The final section should run only in the last retry otherwise it will be run multiple times
    // at the end of all retries.
    if (parameters.retryCount == 1) {
      echo('Run finally section on the intermediate node/agent')
    }
  }
}
