# Java class name analysis (GitHub code base)

Analyzing occurences (i.e. most popular) Java class names published on GitHub using GitHub API + Kotlin.

## Usage

* Configure based on your needs in the [config file](.config.json), leave everything default (apart from the token, you will need to enter this) if you want to check all of GitHub's repositories.
* `gradle run` to get dependencies and run the script
* Find your results exported in the [results folder](/results)

A graph of a test run over the entire code of the first 1000 GitHub users (by ID) can be found [here](/graphs/most-used-plot.png). This took about 15min to execute and when implementing parallized computation will be significantly faster.
