# Adding Smoke E2E for a Language SDK
For the steps below, take _statefun-smoke-e2e-golang_ module as a reference implementation. You can copy the static config/protobuf files from there.

## Implementation
### Step 1: Create a new module and generate the protobuf bindings
* Create a new module named _statefun-smoke-e2e-LANGUAGE_ under _statefun-e2e-tests_.
* Make _statefun-smoke-e2e-multilang-base_ as parent of the created module in ``pom.xml``.
* Copy ``commands.proto`` from the reference implementation into the created module under ``src/main/protobuf``. 
* Make language specific changes to the ``commands.proto``. For example, the following line is added into ``commands.proto`` to generate GoLang bindings:
```
option go_package = ".;app";
```
* Generate language specific protobuf bindings under ``src/main/LANGUAGE``. This enables the function to serialize/deserialize the messages sent/received.
* Copy ``remote-module/module.yaml``, ``Dockerfile``, ``log4j.properties`` into ``src/test/resources``. These files are for launching the Smoke E2E driver, hence no code changes needed.

### Step 2: Implement the CommandInterpreterFn as an HTTP endpoint
* ``CommandInterpreterFn`` is a remote function that performs the operation based on the command received. Following is the high level introduction to the logic to be implemented in the function. One can also look into other Smoke E2E's implementation for reference.
  * The function receives ``SourceCommand``s generated by the ``CommandGenerator``(see details in _statefun-smoke-e2e-driver_).
  * The function extracts ``Commands`` from the ``SourceCommand``, and performs corresponding operations based on the command types:
    * ``Command.Send``: send the enclosing ``Commands`` to the target address.
    * ``Command.SendAfter``: send the enclosing ``Commands`` to the target address after a certain delay of time(defined as a constant, say, 1 millisecond).
    * ``Command.IncrementState``: increment the counter(state) by 1.
    * ``Command.SendEgress``: send a message to the ``DISCARD_EGRESS``. The message can be just a simple string with whatever content.
    * ``Command.Verify``: build a ``VerificationResult`` by filling in the expected count from the ``Command.Verify``, and the actual count from function's counter(state). Then, send it over to the ``VERIFICATION_EGRESS``.
  * Make sure the typename of the function, message, and ingress/egress are all aligned with the driver's definition. For example, the typename of ``VerificationResult`` should be _statefun.smoke.e2e/verification-result_. Think of the typename is the unique identifier of messages across different language bindings. You can find all the naming in ``Constants`` under _statefun-smoke-e2e-driver_.
* Finally, wrap the ``CommandInterpreterFn`` as an HTTP endpoint using a simple web container. The endpoint should listen on 8000 port, aligning to the ``module.yaml`` setting.

### Step 3: Orchestrate pieces into a SmokeVerificationE2E
* Create a language specific SmokeVerificationE2E under ``src/test/java``. Most of the code can just be copied from other Smoke E2E implementations. Particularly, you should focus on preparing resources for launching the language specific HTTP endpoint in the ``configureRemoteFunction`` method.
* Next, create a ``Dockerfile.remote-function`` under ``src/test/resources``, which builds the resources prepared by SmokeVerificationE2E into a Docker image. The image is then launched as an HTTP endpoint inside the container serving the ``CommandInterpreterFn``.

## Execution
### Running the Harness test
* The Harness test is to run the Smoke E2E test as a JUnit process against the remote function Java process. A nice feature it provides is you can run both the Harness and the remote function on an IDE, therefore allows you to set breakpoints on either one and debug the code efficiently.
* How To:
  * Startup the HTTP endpoint that serve the remote function(implemented in Step 2) at port 8000, which aligned with the ``module.yaml`` defined in _statefun-smoke-e2e-multilang-harness_.
  * Comment out ``@Ignore`` in ``MultiLangSmokeHarnessTest``.
  * Run ``MultiLangSmokeHarnessTest``.
* Noted that the Harness requires only Step 1 and Step 2 to be implemented, hence you can use the Harness to test out your implementation.

## Running the SmokeVerificationE2E
* The SmokeVerificationE2E, unlike Harness, spawn up the Flink cluster(master, workers) and the remote function as dedicated containers for testing. Such a setup is to simulate the production environment.
* Once the Step 1,2,3 are implemented, you should be able to run the language specific SmokeVerificationE2E directly.

# Runtime Architecture

```
Flnik StateFun cluster -> SimpleVerificationServer
    (Containers)              (JUnit Process)
          ^
          |
          ˇ
   Remote Function
    (Container)
```

The ``SmokeRunner`` orchestrates the entire Smoke E2E runtime and does the following:
* Launch the Flink StateFun cluster and the remote function, those are defined by ``StatefulFunctionsAppContainers.Builder`` and passed to the ``SmokeRunner`` as an input parameter. The detailed configuration can be found in concrete implementation such as ``SmokeVerificationGolangE2E``
* The ``CommandGenerator`` wrapped by ``CommandFlinkSource`` starts to generate commands and sent them over to the remote function for state manipulations(counters). At the same time it also applies the commands internally to its internal states.
* After commands are generated, The ``CommandFlinkSource`` enters the verification stage and starts to send out ``Command.Verify`` messages along with the counts stored in its internal states as expected counts.
* The remote function encloses the expected counts(from ``Command.Verify`` messages), and the actual counts(from remote function states) into ``VerificationResult`` messages.
* The ``VerificationResult`` messages are then sent to ``SocketClientSink``, and further sent to ``SimpleVerificationServer`` that collects the verification results.
* The ``awaitVerificationSuccess`` method in ``SmokeRunner`` keeps polling the result arrived at ``SimpleVerificationServer`` and verifies it by simply doing actual == expected evaluation. When all the messages are successfully verified, the program exits.

# Framework Modules

## _statefun-smoke-e2e-common_
Testing utilities such as``SmokeRunner``, which is the facet class that organizes the Smoke E2E runtime architecture.

## _statefun-smoke-e2e-driver_
The core logic of the Smoke E2E driver. The driver code here is built into a self-contained jar that can be loaded and ran by the Flink StateFun cluster directly.

## _statefun-smoke-e2e-multilang-base_
This module contains a generic ``pom.xml`` that have the dependencies and the driver jar downloaded to run the Smoke E2E.

## _statefun-smoke-e2e-multilang-harness_
The Harness test that run the Smoke E2E as a JUnit process. Noted that one should have a remote function running at localhost 8000 port for Harness test to interact with.
