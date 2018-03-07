# JMS XmlCall Fabric Adapter

This is an universal adapter to invoke Hyperledger Fabric chaincode using JMS service.
Requests and replies are encoded using XML.

more details in docs directory.

# Directory structure
* `xmlcall-core`: core of xmlcall implementaiton. Classes to parse input XML messages and build XML results.
  There is an utility to generate wsdl and xsd files.
* `xmlcal-jms`: Adapt xmlcall as JMS endpoing. Receives JMS TextMessage on a specified channel
* `xmlcall-ws`: Almost complete xmlcall SOAP adapter. The code works, while is not fully intergrated into testing process.
  The XmlCall adapter works using protobuf service descriptor file, just like JMS.
  
  However to test the SOAP implementaiton xjc is used to marshall/unmarshall SOAP XML messages:
  
  * build protobuf's desc file. For examples, see xmlcall-test/build.gradle protobuf section.
  * from protobuf desc file, xmlcall-code's proto2wsdl utility can be used to generate single XSD file
    ```sh
    $ java com.luxoft.xmlcall.wsdl.proto2wsdl.Main -schema -output data/proto/services.xsd  data/proto/services.desc
    ```
  * Then XML factory is creates using xjc. see xmlcall-ws/generate-jaxb.gradle, genJaxb task.
  
# Run tests in integrated mode

* prepare fabric network:
    ```sh
    $ ./gradlew networkUp
    ```

* Configure channels and deploy chaincodes
    ```sh
    $ ./gradlew networkConfigure
    ```

* Ensure activemq message proker is running.

* Run application. Ensure `fabric' spring profile is active:
    SPRING_PROFILES_ACTIVE=fabric ./gradlew bootRun 

Note:
* protobuf service descriptor file located in data/proto/services.desc. File can be regenerated using
  ```sh
  $ ./gradlew generateProto
  ```
  actual implementation and protobuf handling is located in xmlcall-test/build.gradle. see protobuf task.

* Network configuration is in fabric/fabric.yaml.
* fabric/mkconfig can be used to regenerate network configuration (when config.tx or crypto-config has been changed).

* fabric network can be shut down using
    ```sh
    $ ./gradlew networkDown
    ```
