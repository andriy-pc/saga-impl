## Implementation of Saga choreography pattern ##

### Bootstrap ###

To bootstrap this project you need docker and docker-compose. <br>
You have to open the terminal in the project root directory. Then you have to build `jar`s for all
the modules:

    mvn clean install

The next command will bootstrap all the necessary components inside of docker containers:

    docker-compose up -d

### Details ###

This project consists of 4 executable modules and 1 common module that contains only DTOs

* Executable:
  * order-service
  * user-service
  * warehouse-service
  * shipment-service
* Common:
  * common-dto

#### Order-service ####

The main purpose of this service is to receive an "order"

    curl --location --request POST 'http://localhost:5001/orders' \
    --header 'Content-Type: application/json' \
    --data-raw '{
    "userId": "1",
    "productId": "1",
    "orderedQty": "1"
    }'

After the service received an order - it emits `OrderProcessingEvent`. The rest of the services
reacts to this event.

#### User-service ####

This service listens to the `OrderProcessingEvent`. Before processing the event, the service checks
if the `userProcessingStatus` is `ProcessingStatus.UNPROCESSED`.
To implement the roll-back transaction `userProcessingStatus` can become `ProcessingStatus.REVERT`.
The service will successfully process an event only if the user has sufficient balance. That means
that if the user with ID 1 has 100$, he can not spend more than that.
**The result of the successful processing of this service is the decreased balance of the user.**

To see the list of all the available users you can execute this request:

    curl --location --request GET 'http://localhost:5003/users'

To simplify the implementation - the HashMap is used instead of DB.

#### Warehouse-service ####

This service listens to the `OrderProcessingEvent`. Before processing the event, the service checks
if the `warehouseProcessingStatus` is `ProcessingStatus.UNPROCESSED` AND `userProcessingStatus`
is `ProcessingStatus.SUCCESS`.
This additional condition was added to make the warehouse service process an event **only** after
the **user-service**.
The service will successfully process an event only if an ordered qty is less or equal to the stock
qty.
That means that if an ordered qty > stock qty - the transaction will fail, and the `user-service`
would have to handle the roll-back transaction. That means that decreased user balance will be
increased.
**The result of the successful processing of this service is the decreased qty of the ordered
product.**

To get the list of all available products you can execute this request:

    curl --location --request GET 'http://localhost:5004/warehouse/products/available'

To simplify the implementation - the HashMap is used instead of DB.

#### Shipment-service ####

This service listens to the `OrderProcessingEvent`. Before processing the event, the service checks
if the `shipmentProcessingStatus` is `ProcessingStatus.UNPROCESSED` AND `warehouseProcessingStatus`
is `ProcessingStatus.SUCCESS`.
This additional condition was added to make the warehouse service process an event **only** after
the **warehouse-service**.
This service simulates applying for shipment to the 3rd party service. There is a random generator
that generates a random boolean to indicate if the shipment applying was successful.
If the shipment application failed - then all the previous transactions should be reverted.


