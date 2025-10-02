You are a Java specialist .

I want to create a Java application to perform static analysis of Java applications (or WAR). The application will exists as a Maven module and a command-line application.

The command-line tools take various commands.

## Inventory command

One of the command is `inventory`.

The `inventory` command will the following parameters : 
* `--source` : the optional path to the source of the application
* `--binary` : the optional path to the JAR files of the application
* `--war` : the optional path to the WAR files of the application
* `--output`: the result of the analysis ( CSV ), Default is inventory.csv value.
* `--encoding`: the default encoding to use to read the files. By default the platform encoding is used.
* `--java_version`: the java version of the source file. Mandatory if --source is used
* `--inspector`: the list of inspectors to use ( comma separated list of inspector names). Optional. By default everything is uded.

The purpose of this command is to create an inventory of the application classes as a spreadsheet.

First step is to create an inventory of the classes of the application.

* The list of classes are the combination of the source files and the class files inside the JAR files. Obviously we can have the same class in both the JAR and the source files.
* We create a table with all the classes ( and properties ,class name, file location, binary class location)

We have a list of inspectors. Each inspector is analysing the class and returns a value that is stored in a specific column.

Each inspector has a name, and a column_name that is dedicated to the result of the indicator.

An inspector can supports either source file analysis or binary class analysis or both. ( it depends if the inspector extends a BinaryClass analyzer or a SourceFile analyzer).

Then we iterate on each class :
* We create a new row for the spreadsheet
* For each class, we iterate on each inspector.
* If the inspector supports the class type ( source or binary), we run the inspector and store the result in the corresponding column.
* If the inspector does not support the class type, we store a N/A value in the corresponding column.
At the end of the process, we have a spreadsheet with all the classes and the result of each inspector.

The inventory will contains the following information :
* The class name
* The location ( if it is a source file, the file location, if it is a class file, the JAR)
* The result of each inspector

For the source code analyzer, we will support various types of inspectors provided as abstract base classes : 
* SourceFileInspector : the base class for all source file inspectors
* RegExpFileInspector : returns true of false if the pattern matches
* CountRegexpInspector : returns the number of occurences that matches the regexp.
* TextFileInspector : returns a String with the content of the source file.
* SonarParserInspector : parse the source file using SonarSource Java parser available as a Maven dependency
* RoasterInspector : use RoasterLibrary to parse the source code : https://github.com/forge/roaster
* JavaParserInspectore : use https://github.com/javaparser/javaparser to parse the source code

All these inspectors have a generic interface that provides the main methods decorate(Clazz classToDecorate).

For the binary class analyzer, we will support various types of inspectors provided as abstract base classes :

* BinaryClassInspector : the base class for all binary class inspectors
* ASMInspector : use ASM library to parse the class file : https://asm.ow2
* BCELInspector : use Apache BCEL library to parse the class file : https://commons.apache.org/proper/commons-bcel/
* JavassistInspector : use Javassist library to parse the class file

All these inspectors have a generic interface that provides the main methods decorate(Clazz classToDecorate).

The application will provide a set of default inspectors. The user can also create its own inspectors by extending the base classes and providing the implementation of the decorate method.

To begin with we provide the following inspectors :

- cloc : returns the number of lines of codes ( using a source inspector )
- type : returns the type of declaration using a binary inspector ( class, interface, record, enum etc)

