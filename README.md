# Camunda Community Jenkins Shared Library

[![](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)

A [Jenkins Shared Library](https://www.jenkins.io/doc/book/pipeline/shared-libraries/) with custom Jenkins Pipeline steps that solve generic CI/CD problems.

At the moment it's mainly created to share the [conditionalRetry](docs/conditionalRetry.md) step.

## Usage
After [defining Camunda shared library](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#global-shared-libraries) in your Jenkins instance, you can add it to your pipeline (e.g. your `Jenkinsfile`):
```
@Library('camunda-community') _

// The reset of the Pipeline.
```

## Steps documentation
* [conditionalRetry](docs/conditionalRetry.md)

## Contributing
We value all feedback and contributions. If you find any issues or want to contribute,
please feel free to [fill an issue](https://github.com/camunda-community-hub/camunda-jenkins-shared-library/issues),
or [create a PR](https://github.com/camunda-community-hub/camunda-jenkins-shared-library/pulls). And don't forget to read the [contribution guidelines](CONTRIBUTING.MD)
before contributing.

## License
This is open source software licensed using the Apache License 2.0. Please read the full [LICENSE](LICENSE) for details.
