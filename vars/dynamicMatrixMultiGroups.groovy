#!/usr/bin/env groovy

/*
  dynamicMatrixMultiGroups

  A custom step extends "dynamicMatrix" to create a matrix with heterogeneous groups. It is useful when you want to
  run stages in parallel but with different commands.

  Dependencies:
    - vars/dynamicMatrix.groovy

  Parameters:
    @param List<Map> matrixGroups - DEFAULT: None, OPTIONAL: No
      List of "dynamicMatrix" config to run all of them in parallel.

  Example:
    Here is an example for a pipeline that runs multi groups in parallel:

    ```
    stage('multi_groups_matrix') {
        steps {
            dynamicMatrixMultiGroups([
                [
                    failFast: false,
                    axes: [
                        PLATFORM: ['windows']
                        BROWSER: ['edge', 'safari'],
                    ],
                    actions: {
                        stage("${PLATFORM}_${BROWSER}") {
                            bat 'echo this is a Windows command'
                        }
                    }
                ],
                [
                    failFast: false,
                    axes: [
                        PLATFORM: ['linux', 'mac', 'windows'],
                        BROWSER: ['chrome', 'firefox']
                    ],
                    actions: {
                        stage("${PLATFORM}_${BROWSER}") {
                            sh 'echo this is a Linux command'
                        }
                    }
                ]
            ])
        }
    }
    ```

*/

void call(List<Map> matrixGroups = []) {
  Map matrixGroupsStages = [:]

  matrixGroups.each { matrixGroup ->
    matrixGroup['returnStages'] = true
    matrixGroupsStages << dynamicMatrix(matrixGroup)
  }

  parallel(matrixGroupsStages)
}
