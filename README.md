# sbt remote control

This is an API for controlling [sbt](http://scala-sbt.org/) from
another Scala program. It would be used for example by command line tools,
IDEs, [Activator](https://github.com/typesafehub/activator),
etc. to inspect a project build and run build tasks such as compilation.

In essence sbt-remote-control converts sbt from a command line tool into a
general backend for any kind of user interface, command line or GUI.

## Using

TODO - Let's write some documentation, for now check out the tests.


## Developing

Use [sbt](http://scala-sbt.org/) to build the project. Make sure you have an SBT launcher, and run it in the checked out directory.


Some possibly-still-relevant architectural information can be
found [on this wiki page](https://github.com/sbt/sbt/wiki/Client-server-split).

### Testing

There are two types of tests:  unit and integration.   To run all unit tests, simple run:

    sbt> test


To run the integration tests, which operate inside the sbt-launcher environment, run:

    sbt> integration-tests



### Publishing

This project currently publishes to typesafe's ivy-releases repository.  To cut a distribution, first tag the project via:


    git tag -u <your PGP key> v<version>


Then start or reload sbt:


    sbt> reload


You should see the tagged version of the project if you run the `version` command in sbt.  Now just run `publish-local` and the release is out.


## License

This software is licensed under the [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0).
