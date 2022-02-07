# conditionalRetry

A custom step to do advanced conditional retry within a stage in Jenkins Declarative and Scripted Pipelines.
It's useful to run long running pipelines with many stages on Preemptible/Spot infrastructure.

## Why

This step has been developed to overcome the limitation of the Jenkins' built-in
[retry](https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/#retry-retry-the-body-up-to-n-times) step 
since it has a naive retry mechanism.

Also given that there are no plans from Jenkins side to support restart pipeline from certain stage, 
this tries to address the tickets no.
[JENKINS-53167](https://issues.jenkins.io/browse/JENKINS-53167),
[JENKINS-45455](https://issues.jenkins.io/browse/JENKINS-45455),
and [JENKINS-33846](https://issues.jenkins.io/browse/JENKINS-33846).
The `conditionalRetry` step tries to avoid the need to restart the whole pipeline in the first place
(or at lease minimizing it).

## Examples

### 1. Using conditionalRetry

Here is an example for a pipeline that runs this custom method,
and for glossary and more details please check the [step docs](../vars/conditionalRetry.groovy).  

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
        stage('ConditionalRetryDemo') {
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

### 2. Using conditionalRetry with Jenkins Declarative Matrix

When `conditionalRetry` is used with
[Jenkins Declarative Matrix](https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-matrix)
the parameter `stageNameFilterPatterns` should be set with the matrix variables since all stages in the matrix
have the same name and till these words, having dynamic stage names is not supported in Jenkins.
(more details about that issue in [JENKINS-61280](https://issues.jenkins.io/browse/JENKINS-61280)

Here is an example how to match errors according to the matrix variables
where each stage in the matrix will match different pattern.

```
@Library(['camunda-community', 'logparser']) _

pipeline {
    agent {
        node {
            label 'jenkins-job-runner'
        }
    }
    stages {
        stage('TestMatrix') {
            matrix {
                axes {
                    axis {
                        name 'PLATFORM'
                        values 'linux', 'windows'
                    }
                    axis {
                        name 'BROWSER'
                        values 'firefox'
                    }
                }
                stages {
                    stage('ConditionalRetryDemo') {
                        steps {
                            conditionalRetry([
                                agentLabel: 'spot-instance-node',
                                suppressErrors: false,
                                stageNameFilterPatterns: [".*PLATFORM = '${PLATFORM}', BROWSER = '${BROWSER}'.*", "ConditionalRetryDemo"],
                                useBuiltinFailurePatterns: false,
                                customFailurePatterns: ['linux-pattern': '.*DummyFailurePatternLinux.*', 'windows-pattern': '.*DummyFailurePatternWindows.*',],
                                runSteps: {
                                    sh '''
                                        if [[ "${PLATFORM}" = "linux" ]]; then
                                            echo 'This is just an error with a DummyFailurePatternLinux that will be matched in Linux stage'
                                            exit 1
                                        fi

                                        if [[ "${PLATFORM}" = "windows" ]]; then
                                            echo 'This is just an error with a DummyFailurePatternWindows that will be matched in Linux stage'
                                            exit 1
                                        fi
                                    '''
                                },
                            ])
                        }
                    }
                }
            }
        }
    }
}
```

**NOTE:**

Given the fact that all stages have the same name inside the Jenkins declarative matrix,
the parameter `stageNameFilterPatterns` must be used anyway inside declarative matrix even if the expected
error pattern is the same in all stages.

If you don't want to deal with that you can use the [dynamicMatrix](../docs/dynamicMatrix.md) step which also provides
a matrix with better visualization and extra customization options.
