#!/usr/bin/env groovy

/*
  dynamicMatrixMultiCombinations

  A custom step extends "dynamicMatrix" to create a matrix with different axes. It is useful when you want to
  run stages in parallel but with diffrant varisables combination.

  Dependencies:
    - vars/dynamicMatrix.groovy

  Parameters:
    It takes exactly the same as "dynamicMatrix" but the axes parameter is a list of axes cCombinations.

  Example:
    Here is an example for a pipeline that runs multi cCombinations in parallel:

    ```
    stage('multi_combinations_matrix') {
        steps {
            dynamicMatrixMultiCombinations([
                failFast: false,
                axes: [
                    [
                        PLATFORM: ['windows']
                        BROWSER: ['edge', 'safari'],
                    ],
                    [
                        PLATFORM: ['linux', 'mac', 'windows'],
                        BROWSER: ['chrome', 'firefox']
                    ],
                ],
                actions: {
                    stage("${PLATFORM}_${BROWSER}") {
                        sh 'echo ${PLATFORM}, ${BROWSER}'
                        sh 'echo ${MATRIX_STAGE_NAME}'
                    }
                }
            ])
        }
    }
    ```

*/

void call(Map parameters = [:]) {
  Map matrixStages = [:]
  def axesCombinations = parameters.axes

  axesCombinations.each { axesCombination ->
    parameters.axes = axesCombination
    parameters.returnStages = true
    matrixStages << dynamicMatrix(parameters)
  }

  parallel(matrixStages)
}
