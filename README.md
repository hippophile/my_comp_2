# my_comp_2 (the sequel, nobody asked for it)

its a **MiniJava semantic analyzer**. it take a `.java` file (but like, minijava, not real java, dont be confuse) and check if ur types make sense and then print where all the fields and methods lives in memory. very cool. very useful. wow.

## what does it even do

1. **parse** the file — javacc do this, we did not wrote it
2. **collect** all class/field/method declarations (first pass, because forward references is a thing apparently)
3. **validate** the hierarchy — no cycle, no fake parent class, no lying
4. **validate** the types — u cant use class `Dragon` if u never declare `Dragon`, sorry
5. **check overriding** — if u override a method, the signature better match or we yell at u
6. **compute offsets** — figure out where each field and method pointer live in memory (bytes!! real ones)
7. **type check** everything — (coming soon, we r working on it, chill)

## how to compile

```bash
javac -cp . *.java
```

if it error, make sure ur in the right directory. if ur not sure which directory that is, think harder.

## how to run

```bash
java Main yourfile.java
java Main file1.java file2.java file3.java
```

it will print the offsets and either say nothing (good) or yell an error (bad, fix ur code).

## example output

```
-----------Class Element-----------
--Variables---
Element.Age : 0
Element.Salary : 4
Element.Married : 8
---Methods---
Element.Init : 0
Element.GetAge : 8
```

## field sizes (important!! read this!!)

| type      | size   |
|-----------|--------|
| boolean   | 1 byte |
| int       | 4 byte |
| anything else (class, int[]) | 8 byte (pointer) |

no alignment padding. we pack it tight like sardine.

## file structure

```
my_comp_2/
├── Main.java                  — entry point, does the thing
├── SymbolTable.java           — the brain
├── ClassInfo.java             — one class worth of info
├── FieldInfo.java             — one field worth of info  
├── MethodInfo.java            — one method worth of info
├── ClassCollectorVisitor.java — first pass, collect everything
├── TypeCheckVisitor.java      — second pass (TODO: finish this)
├── minijava/                  — generated AST nodes and visitor infrastructure (dont touch)
├── tests/                     — test files, both correct and incorrect ones
└── javacc.jar, jtb.jar        — the tools that made the parser possible
```

## known issue

- type checking not done yet, we get there when we get there
- the readme have some grammatical error, this is intentional (probably)
