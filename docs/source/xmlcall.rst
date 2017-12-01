Hyperledger Fabric
==================

`Hyperledger Fabric <https://www.hyperledger.org/projects/fabric>`_
is a distributed ledger platform. It has no notion of cryptocurrency,
assets, or any other finantional concepts, found in platforms like
BitCoin, Ethereum, Ripple, and others. Instead it concentrates on
being a framework to build distributed applications, using distributed
storage.

*Hyperledger Fabric* (Fabric for short) provides the same guaranties
as you might expect from any other blockchain platform: it's a
distributed, highly available, scalable, secure platform which allows
no history modification. *Hyperledger Fabric* is oriented towards
private networks, so it delivers a better level of privacy and
confidentiality comparing to public-oriented platforms, like BitCoin
or Ethereum.

From a developer point of view *Hyperledger Fabric* can be seen as
follows:

Network topology
----------------

There is a *Fabric* network, which consists or nodes with different
roles:

* **peer**: Peer is a node which hosts chaincode, and maintains
  state database.

* **orderer**: Orderer is a node, which responsible on total
  transactoins ordering.

Logical topology
----------------

*Fabric* network is organized into non-overlapping *channels*. Each
channel has it's own ledger, state database, set of *peers*,
*orderers*, *users*, *chaincode* instances, and *state database*.

Peers and orderers descibed above. Note that each peer and orderer
node can participate in several channels. However the channels are
isolated from each other. It is impossible for chaincode to access
data from the other channel directly (queriying database or examining
disk content). The only way is to perform chaincode member query.

**Users** is an entity, identified with the private key/certificate
pair, which can send *transactions* to the channel. These transactions
are stored on the ledger.

**Chaincode** is an service, created by application
developer. Chaincode is instantiated in a channel and consists of a
set of members. Each member has a name, parameters, and can return a
value. *Hyperledger Fabric* supports several languages to create
chaincode. Currently these are Go and JavaScript.

**Transaction** is a tuble *(chaincode-id, member-name, invokation
arguments, invocation result, database-delta)*. Transactions are
created by application sdk, and supplied to the blockchain, as
descibed in *"Transaction Processing"*.

**Ledger** is a persistent sequence of transactions. It's a file,
stored on the peers and orderers.

**State database** is a database which is accessible by chaincode.
State database is changed by the stored transactions, using
'database-delta' field.

Transacton processing
---------------------

Transactions in *fabric* go through several stages:

#. Transaction proposal. Application creates a transaction proposal
   which consists of the chaincode member name and supplied arguments
   and send them to the peers. Each peers executes the requested
   member, evaluate the result and database-delta, puts all of
   these together into transaction object, signs that object and
   returns to the requesting peer.
   Note that at this stage nither ledger nor state database is
   modified.

#. Application supplies all the signed proposals to the orderers. The
   orderers validate the signatures, and come to agreement about the
   transactions order.

   .. note:: This step might be done using some consensus
      algorithms. Currently *fabric* supports *kafka*-based ordering
      and *solo* ordering. *Kafka* orderer, as the name implies, use
      kafka cluster to ensure the total transactions order. In this
      mode consensus is fault-tolerand. Another mode, which is used
      for development is *solo*. in that mode only a single orderer
      per channel exists and creates a single point of failure.

#. Orerers deliver new transaction in the final order to the peers.

#. Peers finaly validate transaction *database-delta*, and if there is
   no conflict between state database as it used to be at the moment
   of proposal validation (see the step 1), the delta is applied to
   the database, thus bringing it to the new state.

   Then application is notified whether transaction has finally been
   accepted or not.


Besides issuing transaction, application can query state database
using one of read-only chaincode members. These queries do not change
the state database and consists of a single peer request.


XML Call service
================

Motivation
----------

*Hyperledger Fabric* provides two more or less mature SDK's currently:

* node.js SDK
* Java SDK

Both SDKs implements the full transacton processing, as described
above, so they are pretty low-level, highly *Hyperledger Fabric*
specific, and require good knownledge of *Fabric* transaction
processing.

*Hyperledget Fabric* doesn't enforce any structure on the
parameters. Each chaincode member accepts an array of byte strings,
and is free to interpret these in any way an application developer
feels appropriate..

Due to these reasons (as well as some others) an `xmlcall` facade was
developed, hiding configuration and transaction processing flows from
application developers.

The main idea behind it is that *fabric-xmlcall* is an service, which
can be used to invoke chaincode using more descriptive arguments,
rather than raw byte string. Currently xmlcall supports XML-encoded
requests on input and generates XML-encoded results as well.

Note that *fabric-xmlcall* provides platform-neutral interface, and
might be ported to other blockchains in the future.

Implementation information
--------------------------

*xmlcall* is developed on top of these three things:

#. Self-descriptive invocation language.
#. JMS transport
#. protobuf desciptors.

Self-descriptive invocation language
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

We used XML as a language of choice to accept requests and generate
results. XML is a broadly used language in the Enterprise community
with lots of tools to create, parse, validate, transform, and so on
for almost each programming language.

.. note:: JSON is another alternative and support for it might be
	  implemented one day.

JMS transport
~~~~~~~~~~~~~

We use JMS at transport layer. *xmlcall* has been developed and tested
with `Apache ActiveMQ <http://activemq.apache.org/>`_ and `Spring framework
<https://spring.io/>`_.

JMS is choosen since it can handle hatively requests, replies,
and events. However SOAP support might be added one day.

One needs to send plain JMS textmessage with XML document embedded
with no other escapes to process. Results are retuned in the same way.

Protobuf desciptors
~~~~~~~~~~~~~~~~~~~

*xmlcall* uses propobuf as it's ipc description language. Protobuf was
choosen due to some reasons, amonng then:

#. It has already been selected at communication layer for our other
   projects.
#. It's future right language, shich allows as to descibe almost all
   the aspects and details of invocation.
#. It has broad community and extensive support from both community
   and Google.

Outline
--------

As a general outline, the xmlcall adapter is used like that:

#. Interface to chaincode is defined using *protobuf* proto
   files. Chaincode is defined as ``service``, and all the members are
   defined as ``rpc`` entries.

#. *.proto* files are compiled to descriptors using ``protoc``
   compiler to build desciptor files.

#. Descriptor files can be used to generate XSD schema for the sake of
   application development.

#. XmlCall adapter starts with these descriptor files and accepts JMS
   requests as ``<ServiceName.MethodName>`` XML document.

#. Invocation or query request submitted to blockchain.

#. When result is ready, reply is send to application via
   ``<TypeName>`` XML document.

#. If call fails, result is delivered as ``<ChaincodeFault>`` XML
   document.


Usage in Depth
--------------

In order to use *xmlcall* an blockchain service should be described as
a gprc service.

.. note:: *xmlcall* itself has no relation to grps, it only uses the
	  augmented grpc desciptors.

Imagine we have an ``Counter`` service, exposing following members
with obvious semantics:

* ``addAndGet(integer) -> integer``
* ``getValue() -> integer``

Start with descibing the necessary types in protobuf:

.. code-block:: protobuf

   // counter.proto
   syntax = "proto3";
   package counter;

   // a type to be used as an argument and result
   message Value
   {
       int32 value = 1;
   }
   
Now, ``Value`` message coild be passed to the chaincode members. Let's
sketch it now (use Java as a prototype language - implementation is
not important to us):

.. code-block:: java

   class Counter {
       int current = 0;
       
       public Value addAndGet(Value value) {
           current = value.getValue();
       }

       public Value getValue() {
           return Value.newBuilder().setValue(current).build();
       }
   }

Having ``Value`` message defined, add a service information to the
counter.proto:

.. code-block:: protobuf

   // counter.proto
   syntax = "proto3";
   package counter;

   //include the necessary xmlcall definitions
   import "xmlcall.proto";
   
   // include Empty message to follow protobuf's conventions
   import "google/protobuf/empty.proto";

   // a type to be used as an argument and result
   message Value
   {
       int32 value = 1;
   }

   service Counter {
      rpc addAndGet(Value) returns (Value) {
          option(xmlcall.exec_type) = INVOKE;
      }
      rpc getValue(google.protobuf.Empty) returns (Value) {
          option(xmlcall.exec_type) = QUERY;
      }
   }


So, our ``Counter`` service contains two members defined:
``addAndGet`` and ``getValue``. Note that ``getValue`` member follows
grpc's convention: Each service member accepts exactly one argument
and returns one argument.

The ``xmlcall.exec_type`` option is mandatory and declared how
corresponding method should be executed - as a teansaction invocation
or as a query.

Next step is to generate protobuf descripotors out of these:

.. code-block:: console
		
   $ protoc --descriptor_set_out=counter.desc  --include_imports \
            counter.proto

This command generates protobuf's desciptor dile, which contains all
the information from compiled files - all the types, service, etc.

.. note::
   If you use Gradle or Maven both support options to generate
   descriptor file. Refer respective plugin documentation for more
   info.
   
*xmlcall* would read this file and marshall requests using these
types.

So now it would accept following XML request:

.. code-block:: XML

   <Counter.addAndGet
	in.channel="counter-channel"
	in.chaincodeId="counter-chhaincode-id">
      <value>10</value>
   </Counter.addAndGet>

and (assuming current counter state is 1) would reply with following
XML document:

.. code-block:: XML

   <main.Value
	out.txid="<some transaction id string>">
      <value>11</value>
   </main.Value>


XSD generation
~~~~~~~~~~~~~~

Sometimes it might be useful to convert protobuf descriptor into `XML
schema (XSD) <https://www.w3.org/2001/XMLSchema>`_.

xmlcall provides a java class which can do it:
``com.luxoft.xmlcall.wsdl.proto2wsdl.Main``

``proto2wsdl`` might be used to generate single XSD file, which contains
all the necesary definitions:

.. code-block:: console

 $ java -jar xmlcall.jar com.luxoft.xmlcall.wsdl.proto2wsdl.Main \
        -schema \
	-output <target-file> \
	counter.desc

``target-file`` specifies the output file name.

If it is necessary to have seperate files, ``proto2wsdl`` might be
used to generate single xsd per member:

.. code-block:: console

 $ java -jar xmlcall.jar com.luxoft.xmlcall.wsdl.proto2wsdl.Main \
        -schema-set \
	-output <target-dir> \
	counter.desc

Start XML/Call adapter and configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Xmlcall adapter created using Spring and configured using spring
properties:

.. note:: More information on configuring Spring applications can be
   found in `official documentation
   <https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config-application-property-files>`_.


* *descriptorFileName*: compiled descriptor file name. Must be
  specified.

* *xmlCallJmsDestination*: JMS topic name to listen on. Default value
  is 'blockchain-xmlcall'.
  
* *connectorClass*: java class to connect to blockchain. Default value
  is "XmlCallFabricConnector", which implements connection to
  *Hyperledger Fabric* using *fabric-utils* semantics.

  Otherwise it should be a full class name.

* *connectorArg*: connector-specific argument. for
  *XmlCallFabricConnector* this is a path to *config.yaml*.

* *spring.activemq.broker-url*: is a tcp://localhost:61616*

  .. note:: *xmlcall* uses *Apache ActiveMQ* broker as JMS service,
     look for `documentation
     <http://activemq.apache.org/configuring-transports.html>`_ for configuration
     details.  `

Logging
~~~~~~~

*xmlcall* compiled with `slf4j <https://www.slf4j.org/manual.html>`_
logger, backed by `logback
<https://logback.qos.ch/manual/index.html>`_. Refer respective
documentation for configuration details.

Error Handling
~~~~~~~~~~~~~~

If something went wrong with chaincode invocation, an error is
descibed in *xmlcall* logs, and for the client application an XML
document generated:

.. code-block:: xml

   <ChaincodeFault>
     <message>...</message>
   </ChaincodeFault>
