# Text Indexer

## Program description
Inside there is Kotlin library, which is based on coroutines and provides service to index text files based on words.
1. Library's interface allows consumers to operate both with directories and files and to get list of files that 
contain chosen word(s). This library supports multithreading access, and also defines files and directories changes 
in the file system. This library can be easily extended with any words splitting mechanism: simple word splitting,
splitting based on lexers, e.t.c. There is no need to save state between sessions while working with the library.
Напишите на Котлине и корутинах библиотеку, реализующую сервис индексации текстовых файлов по словам.
2. Code is covered with necessary tests and there is UI inside, which allows to add directories/files into the index and 
to make simple queries to search tokens in chosen documents.

## Launch instruction

### Required program software:
* Java >= 17.0.2
* Gradle >= 7.4.2

### Steps to launch application:
1. Build executable jar:

`./gradlew cleanAndBuildFatJar`

2. Run executable jar:

`java -jar ./build/libs/TextIndexer-1.0-SNAPSHOT-standalone.jar`

### Additional program arguments

| Argument                  | Description                           | Default | Values       |
|---------------------------|---------------------------------------|---------|--------------|
| syncDelayTimeInSeconds=10 | Sync task launch frequency in seconds | 10      | Integers     |
| syncStatusIsEnabled=false | Update status during sync             | false   | true / false |
| debugPanelIsEnabled=false | Shows debug panel                     | false   | true / false |

### Future plans

