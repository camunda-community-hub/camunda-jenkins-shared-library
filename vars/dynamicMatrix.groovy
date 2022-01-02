#!/usr/bin/env groovy

/*
  dynamicMatrix

  A custom step mimics Jenkins declarative matrix but with better visualization and extra customization options.

  It's mainly an implementation of https://www.jenkins.io/blog/2019/12/02/matrix-building-with-scripted-pipeline
  as a workaround for declarative matrix limitations https://issues.jenkins.io/browse/JENKINS-61280.

  Parameters:
    @param Map axes - DEFAULT: None, MANDATORY: Yes
    @param Closure actions - DEFAULT: None, MANDATORY: Yes
    @param boolean failFast - DEFAULT: false
      If a stage failed in the matrix, mark the build as failed and don't continue the rest of stages.
    @param String stageNameSeparator - DEFAULT: "_"
      Default stage name separator which used in "MATRIX_STAGE_NAME" exported var. e.g. if the matrix has
      "PLATFORM" and "BROWSER" as axes and "stageNameSeparator" is "_", MATRIX_STAGE_NAME var will be
      "${PLATFORM}_${BROWSER}".
    @param Map extraVars - DEFAULT: [:]
      A map has key value for any vars needed to be passed to the matrix stages.
    @param boolean returnStages - DEFAULT: false
      Instead run matrix stages in parallel, return the stages if extra operations will be applied.
      It's mainly helpful to run matrix with heterogeneous groups (e.g. used in dynamicMatrixMultiGroups step).

  Exported vars:
    - MATRIX_STAGE_NAME: An env var concatenates all matrix stage axes with underscore.
        e.g. if the matrix has "PLATFORM" and "BROWSER" as axes, this var will be "${PLATFORM}_${BROWSER}".
        It's useful to be used as identifier for stages within the matrix.
    - MATRIX_STAGE_VARS: The same as MATRIX_STAGE_NAME but comma separated and includes keys.
        e.g. if the matrix has "PLATFORM" and "BROWSER" as axes, this var will be 
        "PLATFORM=${PLATFORM}, BROWSER=${BROWSER}".

  Example:
    Here is an example for a pipeline that runs a single parallel matrix group:
    ```
    stage('single_matrix') {
        steps {
            dynamicMatrix([
                failFast: false,
                axes: [
                    PLATFORM: ['linux', 'mac', 'windows'],
                    BROWSER: ['chrome', 'firefox']
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

    For examples with multi axes and multi groups, please check "dynamicMatrixMultiCombinations"
    and "dynamicMatrixMultiGroups".
*/

import groovy.transform.Field

@Field String stepName = this.class.name

@NonCPS
List getMatrixAxes(Map axes) {
  List axesFlatten = []
  axes.each { axisName, axisValues ->
    List axisCombination = []
    axisValues.each { axisValue ->
      axisCombination << [(axisName): axisValue]
    }
    axesFlatten << axisList
  }
  axesFlatten.combinations()*.sum()
}

List<String> mapToKeyValueList(Map source) {
  return source.collect { key, value -> "${key}=${value}" }
}

void call(Map givenParameters = [:]) {

  // Mandatory parameters.
  mandatoryParameters = [
    'axes': [],
    'actions': {}
  ]

  mandatoryParameters.keySet().each { mandatoryParameter ->
    if (!givenParameters[mandatoryParameter]) {
      error "[${stepName}] The following parameter is required and must be specified: '${mandatoryParameter}'"
    }
  }

  // Optional parameters.
  defaultParameters = [
    failFast: true,
    stageNameSeparator: '_',
    extraVars: [:],
    returnStages: false,
    // TODO: Add 'exclude' parameter.
  ]

  // Validate given parameters.
  List availableParameters = (mandatoryParameters.keySet() + defaultParameters.keySet()) as List

  givenParameters.keySet().each { parameter ->
    assert parameter in availableParameters,
    echo "[${stepName}] Invalid parameter '${parameter}'. Available parameter are: ${availableParameters}"
  }

  // The parameters var must be copied as a local var (with def) to allow the method to work within loops.
  def parameters = defaultParameters + givenParameters

  Map matrixStages = [
    failFast: parameters.failFast
  ]

  List matrixAxes = getMatrixAxes(parameters.axes)

  for(int index = 0; index < matrixAxes.size(); index++) {
    Map axis = matrixAxes[index]
    List groupVars = mapToKeyValueList(axis)
    String groupID = groupVars.join(', ')

    // Add Matrix common vars.
    groupVars.addAll(
      ["MATRIX_STAGE_VARS=${groupID}", "MATRIX_STAGE_NAME=${axis.values().join(parameters.stageNameSeparator)}"] +
      mapToKeyValueList(parameters.extraVars)
    )

    echo "[${stepName}] Matrix group vars are:\n ${groupVars}"

    matrixStages[groupID] = { ->
      stage(groupID) {
        withEnv(groupVars) {
          parameters.actions()
        }
      }
    }
  }

  if (parameters.returnStages) {
    return matrixStages
  } else {
    parallel matrixStages
  }
}
