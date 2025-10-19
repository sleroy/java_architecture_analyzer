
On the beans 

[text](src/main/java/com/analyzer/rules/ejb2spring/JndiLookupInspector.java)

Review these guidelines and fix the code.


### 🏗️ Architectural Guidelines Established

#### ✅ Correct Pattern (Universal)

1. __Trust @InspectorDependencies__: Handles ALL filtering - never check tags manually in `supports()`

2. __Simple supports()__: Do not do any tag filtering in the supports method. It is done with `@InspectorDependencies`. If the `supports` method do actually nothing, drop the method

3. __Tags vs Properties__:

   - __Tags__: On `ProjectFile` with `projectFile.setTag()` - for dependency chains
   - __Properties__: On `ClassNode` with `classNode.setProperty()` - for analysis data

4. __Honor produces Contract__: If `produces = {TAG_NAME}`, MUST set that tag on ProjectFile

#### ❌ Anti-Pattern (Never Do)

1. __Manual Tag Checking__: Never check `projectFile.getTag()` or `projectFile.getBooleanTag()` in `supports()`
2. __Complex supports() Logic__: File type, language, or extension checking belongs in dependencies
3. __Dependency Duplication__: Don't reimplement what `@InspectorDependencies` provides
4. __Tag/Property Confusion__: Don't mix up when to use tags vs properties
5. __Using multiple properties or tags__ : Do not use multiple tags or properties when they can be combined as a serializable POJO for a single property. 
6. __Use predefined lists instead of long equals chains__: those long chains of equals comparisons should be replaced with predefined collections and contains() method for better readability and performance. 
7. __Do not use directly ProjectFile__ : Use directly the methods from *ProjectFileDecorator* to set tags. 
8. __toJson()__ : methods like that are not necessarily since we can use Jackson for serialize/deserialize. We can store directly the POJO in a property.
9. __Assign rather on ClassNode and Properties__ : most of the tags should be assigned as data structure on ClassNode via properties rather than tags. Tags should be a quick way to identify files with important features and associate also metrics on them.
10. __Tags should be int or boolean or very simple string or enum__: Tag values should be int, or boolean or very simple string or enums. We should avoid semantically equivalent tags.
11. __Tags and producers__: Review the tags listed in producers that the list is up-to-date and valid