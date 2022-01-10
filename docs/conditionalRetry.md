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

## Example

Here is an example for a pipeline that runs this custom method,
and for glossary and more details please check the [step docs](../vars/conditionalRetry.groovy).  

```
@Library('camunda-community') _

pipeline {
    agent {
        node {
            // This is the intermediate node label. It should run on a stable infrastructure. 
            label 'jenkins-job-runner'
        }
    }
    stages {
        stage('Conditional retry demo') {
            steps {
                conditionalRetry([
                    // This is the execution node label. It could run on preemptible/spot infrastructure. 
                    agentLabel: 'spot-instance-nodes',
                    suppressErrors: false,
                    retryCount: 3,
                    retryDelay: 5,
                    useBuiltinFailurePatterns: false,
                    customFailurePatterns: ['test-pattern': '.*FlowInterruptedException.*'],
                    // Demonstrate flaky stage and retry if the pattern found in the logs.
                    runSteps: {
                        sh '''
                            if [[ $(( $RANDOM % 2 )) -eq 1 ]]; then
                                echo FlowInterruptedException; exit 1
                            fi
                        '''
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

## Known issues

The `conditionalRetry` step doesn't work with Jenkins
[Declarative Matrix](https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-matrix) where it use the same name
for all stages and the stage name is used as an identifier for log parsing. In case you need to run `conditionalRetry`
step in a parallel matrix then please use the [dynamicMatrix](../docs/dynamicMatrix.md) step.
