# Algorithm name

In this repository the source code of the algorithm is presented. Please cite this work as:

*Citation not available yet*

## Compile

SBT assembly plugin is employed in this project for a better management of dependencies. Assuming sbt is already installed on your system, for compiling the program just execute the following commands in a terminal:

```
sbt clean
sbt compile
sby assembly
```
The final generated .jar file is under the /target directory.


## Parameters

Parameters of the algorithm are introduced following a Linux-CLI style. For the complete list of commands, run the method with the -h option. The complete list of parameters are summarised below:
```
Usage: <main class> [-BhSv] [--kafkabroker=NAME] [--maxSamples=Number]
                    [--time=SECONDS] [-c=<CROSSOVER_PROBABILITY>] [-C=VALUE]
                    [-e=VALUE] [-l=NUMBER] [-m=<MUTATION_PROBABILITY>]
                    [-n=PARTITIONS] [-p=VALUE] [-Q=SIZE] [-r=PATH] [-s=SEED]
                    [-t=PATH] [-T=PATH] [--topics=NAME(S)]... [-o=NAME(S)[,NAME
                    (S)...]]... trainingFile testFile
      trainingFile          The training file in ARFF format.
      testFile              The test file in ARFF format.
      --kafkabroker=NAME    The host an port of the kafka broker being used
      --maxSamples=Number   The maximum number of samples to process before stop the
                              process
      --time=SECONDS        Data collect time (in milliseconds) for the Spark
                              Streaming engine
      --topics=NAME(S)      A comma-separated list of kafka topics to be employed
  -B                        Big Data processing using Spark
  -c, --crossover=<CROSSOVER_PROBABILITY>
                            The crossover probability. By default is 0.6
  -C=VALUE                  Chunk size for non-big data streaming processing
  -e=VALUE                  Maximum number of evaluations
  -h, --help                Show this help message and exit.
  -l, --labels=NUMBER       The number of fuzzy linguistic labels for each variable.
  -m, --mutation=<MUTATION_PROBABILITY>
                            The mutation probability. By default is 0.1
  -n=PARTITIONS             The number of partitions employed for Big Data
  -o, --objectives=NAME(S)[,NAME(S)...]
                            A comma-separated list of quality measures to be used as
                              objectives
  -p=VALUE                  Population Size. NOTE: Should be a number equals to the
                              size of a n x n grid, e.g., 49 is for 7x7 grids.
  -Q=SIZE                   The size of the FIFO queue of FEPDS
  -r, --rules=PATH          The path for storing the rules file.
  -s, --seed=SEED           The seed for the random number generator. Defaults to 1.
  -S                        Streaming processing
  -t, --training=PATH       The path for storing the training results file.
  -T, --test=PATH           The path for storing the test results file.
  -v                        Show INFO messages.

Process finished with exit code 0

```

## Execution

Default execution:

```
TODO
```

## Study

TODO: