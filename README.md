# Text Indexer

## Program description



## Launch instruction

### Required program software:
* Java >= 17.0.2
* Gradle >= 7.4.2

### Steps to launch application:
1. Build executable jar:

`./gradlew cleanFatJar`

2. Run executable jar:

`java -jar ./build/libs/TextIndexer-1.0-SNAPSHOT-standalone.jar`

### Additional program arguments

| Argument                  | Description                           | Default | Values       |
|---------------------------|---------------------------------------|---------|--------------|
| syncDelayTimeInSeconds=10 | Sync task launch frequency in seconds | 10      | Integers     |
| debugPanelIsEnabled=false | Shows debug panel                     | false   | true / false |
| showSyncStatus=false      | Update status during sync             | false   | true / false |