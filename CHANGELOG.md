# Changelog

## [Unreleased]

## [5.1.0-RC-4]
### Added
- Feature / Scenario / Step / Rule / Hook start methods which are overridable
- JSR-305 annotations
- `buildFinishTestItemRequest` overridable method
### Fixed
- Probable agent crash on cucumber-groovy
### Changed
- Client version updated on [5.1.0-RC-12](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-12)
### Removed
- deprecated code

## [5.1.0-RC-3]
### Changed
- Client version updated on [5.1.0-RC-6](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-6)

## [5.1.0-RC-2]
### Changed
- Client version updated on [5.1.0-RC-4](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-4)
### Fixed
- Possible immutable list modification error in AbstractReporter#getParameters method

## [5.1.0-RC-1]
### Changed
- Client version updated on [5.1.0-RC-1](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-1)
- Version changed on 5.1.0
### Fixed
- [Issue #20](https://github.com/reportportal/agent-java-cucumber6/issues/20): Scenario outlines with dynamic names not supported

## [5.0.6]
### Fixed
- Table parameter handling for different reporters

## [5.0.5]
### Changed
- Data attachments honor names now
- Client version updated on [5.0.21](https://github.com/reportportal/client-java/releases/tag/5.0.21)

## [5.0.2]
### Changed
- Client version updated on [5.0.18](https://github.com/reportportal/client-java/releases/tag/5.0.18)
### Fixed
- Empty interrupted suite in case of duplicate step
- Issue #9: no attributes

## [5.0.1]
### Changed
- Client version updated on [5.0.15](https://github.com/reportportal/client-java/releases/tag/5.0.15)
### Fixed
- 'CHILD_START_TIME_EARLIER_THAN_PARENT' Exception in some cases (issue #7)

## [5.0.0]
### Added
- Docstring parameter handling
### Changed
- Many static methods from Util class were moved to AbstractReporter class and made protected to ease extension
- Client version updated on `5.0.12`

## [5.0.0-RC-1]
### Added
- 'Rule' keyword support
- Callback reporting
### Changed
- CodeRef is now relative
- Test step parameters handling
- Mime type processing for data embedding was improved
### Fixed
- Manually-reported nested steps now correctly fail all parents
### Removed
- Scenario Outline iteration number in item names, to not break re-runs

## [5.0.0-BETA-1]
### Fixed
- Incorrect item type settings
- Attribute reporting
- Test case id annotation on a step definition method reading
### Added
- Nested steps support

## [0.0.2-ALPHA]
### Added
- Test Case ID support
### Fixed
- codeRef reporting was added for every item in an item tree

## [0.0.1-ALPHA]
## Added
- Initial release to Public Maven Repositories
