# dynamicMatrix

Custom step variates to mimic Jenkins declarative matrix but with better visualization
and extra customization options. It's mainly the declarative implementation of the blog post
[Matrix building in scripted pipeline](https://www.jenkins.io/blog/2019/12/02/matrix-building-with-scripted-pipeline)
but with more options to make it easier to run parallel matrix with different combinations and groups.

## Why

Those steps have been developed to overcome the limitation of the Jenkins
[Declarative Matrix](https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-matrix) where it use the same name
for all stages and it doesn't allow to set stage names dynamically.
(more details in [JENKINS-61280 - Allow dynamic stage names](https://issues.jenkins.io/browse/JENKINS-61280))

The main reason of developing this those custom steps is to make [conditionalRetry](../docs/conditionalRetry.md) works
in matrix which is not possible with the Jenkins built-in
[Declarative Matrix](https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-matrix)
because unique stages name is required by [pipeline-logparser](https://github.com/gdemengin/pipeline-logparser)
The issue is already reported in [pipeline-logparser #9](https://github.com/gdemengin/pipeline-logparser/issues/9)
but at the end it's an issue in Jenkins itself.

## Exported variables

The steps make some variables available according to the axes input as following:

- **MATRIX_STAGE_NAME**: An env var concatenates all matrix stage axes with underscore.
    e.g. if the matrix has `PLATFORM` and `BROWSER` as axes, this var will be `${PLATFORM}_${BROWSER}`.
    It's useful to be used as identifier for stages within the matrix.
- **MATRIX_STAGE_VARS**: The same as MATRIX_STAGE_NAME but comma separated and includes keys.
    e.g. if the matrix has `PLATFORM` and `BROWSER` as axes, this var will be 
    `PLATFORM=${PLATFORM}, BROWSER=${BROWSER}`.

## Examples

There are 3 variates where each one of them covers different situation.

The following are examples for each variate, and for more details please check the
[main step docs](../vars/dynamicMatrix.groovy).  

### 1. Single dynamic matrix

The simplest form to run a dynamic matrix is using [dynamicMatrix](../vars/dynamicMatrix.groovy) where you set axes
with multiple variables and you will get a multi-dimensional matrix according to those variables.

Here is an example with different web browsers and operating systems:

```
stage('single_matrix') {
    steps {
        dynamicMatrix([
            failFast: false,
            axes: [
                PLATFORM: ['linux', 'mac', 'windows'],
                BROWSER: ['chrome', 'firefox', 'safari']
            ],
            actions: {
                stage("${PLATFORM}_${BROWSER}") {
                    sh 'echo ${PLATFORM} - ${BROWSER}'
                    sh 'echo ${MATRIX_STAGE_NAME}'
                }
            }
        ])
    }
}
```

With 3 `PLATFORM` variables and 3 `BROWSER` variables, we will get a matrix with `9` variations (e.g. `Linux - Chrome`,
`Mac - Chrome`, etc)).

And as you see here, it will simply make all combinations possible of the axes. But what if some combinations
are not valid? E.g. you probably don't need to have `Linux - Safari` combination since Safari web browser doesn't work
on Linux anyway. Here comes `dynamicMatrixMultiCombinations` where you can set multi combinations for the axes.

### 2. Multi combinations dynamic matrix

If you have different axes combinations but the same stages then [dynamicMatrixMultiCombinations](../vars/dynamicMatrixMultiCombinations.groovy)
could be used for that case.

Here is the same example of the web browsers and operating systems but Safari stage is not generated for Linux:

```
stage('multi_combinations_matrix') {
    steps {
        dynamicMatrixMultiCombinations([
            failFast: false,
            axes: [
                [
                    PLATFORM: ['linux', 'mac', 'windows'],
                    BROWSER: ['chrome', 'firefox']
                ],
                [
                    PLATFORM: ['mac', 'windows']
                    BROWSER: ['safari'],
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

Here we have 3 combinations each one of them has different variables, we will get a matrix with `8` variations
where Safari runs on Mac and Windows only. Not only that, but also it will be easier to add other browsers like
`MS Edge` for example.

As you see here, it will generate the same stages for all combinations, but what if the stages are actually different?
Here comes `dynamicMatrixMultiGroups` where you can set multi groups within the same matrix.

### 3. Multi groups dynamic matrix

If you have different groups or different stages should run in parallel at the same time, then [dynamicMatrixMultiGroups](../vars/dynamicMatrixMultiGroups.groovy)
could be used for that case.

Here is the same example of the web browsers and operating systems but note how the stages are different between
Windows (which runs commands via `bat`) and Unix-like systems (which run commands via `sh`)

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
                    PLATFORM: ['linux', 'mac'],
                    BROWSER: ['chrome', 'firefox']
                ],
                actions: {
                    stage("${PLATFORM}_${BROWSER}") {
                        sh 'echo this is a Unix-like command'
                    }
                }
            ]
        ])
    }
}
```
