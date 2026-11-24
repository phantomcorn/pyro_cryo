# Pyro Cryo
Crypto payment gateway using Spring Boot API and Web3j watcher

## TODO
### Features
- [x] CryptoRail smart contract
- [x] Implement EIP-719 for security
- [x] Merchant generates invoice by calling our backend server using REST API
- [x] Server returns checkout page
- [x] Connect wallet to checkout page
- [x] Connected wallet can pay using the pay button
- [x] Detect PaymentRecieved from our contract on the blockchain and update invoice status
- [x] Any invoice status update sends message to inbox
- [x] Endpoint to register merchant
- [x] KMS for signed webhooks
- [x] Inbox fetches message, creates, signs and sends webhook to merchant endpoint up
- [x] Any webhook that doesn't get a 200 OK status code resends
- [x] Authenticated user to can access api and invoice endpoint
- [x] api key access cannot generate more api key
- [x] Expires unpaid invoice sends `order.expired` webhook to merchant
### Future
- [ ] Buyer can transfer directly through cold wallet (QR code payment)
  - [ ] Monitor token Transfer event incase buyer transfer directly to merchant address using QR code
  - [ ] Detect Transfer event from token contract on the blockchain and update invoice status
- [ ] Refund (webhooks)
  - [ ] Partial refund
### Edge cases (Grilled By AI)
- [x] Adding more watchers. Watcher both detect invoice can finalize. Both update invoice status, both sends webhooks
  - [x] Condition update + Lock
- [x] You mark your invoice as CONFIRMED and calls send webhooks. Just as you are about to send your server crashes -> user doesn't recieve confirmation but your system says invoice is confirmed. (Dual-write problem)
  - [x] When updating status to your invoice also write to outbox database
  - [x] Read from outbox database into queue.
  - [ ] Should not read same row twice
  - [x] Worker processed message from the queue.
  - [x] Once finished, mark entry as processed (Mark before or after? After. Prefer duplicate webhooks mitigate with idempotency over silent loss)
- [ ] Decision: Single background job to read outbox db and store in in-memory queue or multiple?
  - [ ] Single. Multiple adds unnecessary complexity. 
  - [ ] Single may retrieve billions and queue overflows and crash. Set limit on how much is retrieved at a time.
  - [ ] Active-passive failover
    - [x] A network blip promote passive to active but previous active is still alive. Now there's 2 active
        - [x] Introducing a manager introduces a single point of failure
    - [ ] Lease mechanism. Leader renew lease by updating timestamp via condition write. Non-leader check periodically, attempt to acquire lease only when expire.
- [ ] Buyer clicks pay 1 second before expiry, contract `block.timestamp` shows expired. Transaction reverts + gas fee paid
  - [ ] Add grace period in contract
    - [ ] `order.expired` webhook but function still accepts payment within grace period
      - [ ] Two clocks: shorter expiration for user view, longer for merchant and server. Delay `order.expired` webhook and check invoice status again before sending.
  - [ ] Disable click button 1 minute before actual expiry
- [ ] Watcher crashes halfway through processing a block
  - [ ] Checkpoint every processed block
  - [ ] Resume from checkpoint
- [ ] **Merchant** server crash beyond webhook retries. Webhook fails
  - [ ] Webhook retries exhausted, get stored in dead-letter queue
  - [ ] On restart, merchant pull all updates from time of crash

## Features
- `PaymentRail.sol` contract acts as a non-custodial intermediary between buyer and seller leveraging DLT.
- Support standard ERC20 token.
- `pay` REST API function which handles:
    - creating an invoice
    - directing user to checkout page
    - executes PaymentRail contract

## System flow

![System flow](./asset//System%20flow.png)

1. The user clicks checkout → request goes to merchant server → goes to crypto rail server
2. Once its received by our server
    - Many merchant can make this request which must be authenticated to create an invoice
        - Merchant server and our server stores a shared key to prove each other’s identity for auth
    - Once authenticated, our merchant service creates a new invoice in the database. Computes a `digest` and `signature`
    - Returns a link to allow the user to pay, `signature` and `digest`
3. User clicks on the link to direct to payment page
4. Once the user clicks pay, it opens up a web3 wallet to ask the user to sign their contract. (say User decides to pay using token X). Once that completes.
    1. Firstly, `approve(address approver, uint amount)`called on token contract
    2. Secondly, `pay(bytes32 invoiceId, address token, uint amount, address merchantAddr, uint deadline, bytes signature, bytes32 digest)` 
        - More on `pay` below
        - The `signature` and the `digest` here is computed by the server in the step 2.
5. Web3j client subscribes to an ethereum websocket and watches for new block:

    - Check for reorg:
        - Check whether new block's parent hash points to most recent processed block
        - Yes -> Continue
        - No -> Handle reorg

    - Every new head checks for existence of our contract address and `PaymentReceived` event using the bloom filter
    - If it doesn't we simply -> Do nothing
    - If it does -> Grab the logs and scan for all invoice ID. Update status of these invoice to `DETECTED`. Mark that this invoice payment has been done under this block (block number, block hash)
    - Find all invoice that can be updated to `CONFIRMED` by checking whether
    ```
    new block number - block number where our invoice payment was done > N
    ```
    This essentially means there are enough new blocks built ontop that we can guarantee a reorg is very unlikely to happen.
    - Mark this block (block number, block hash) as processed
    - The merchant service and the watcher shares a cryptographic key.
    - Once confirmed, watcher triggers a webhook which our merchant server recieves and update it states. Our frontend polls for changes in our server and directs to a confirmation page when succeed.
    - To make sure the merchant server recieves webhook from the real watcher, a hash of the request is also sent along with the request. The merchant server recalculates the hash using its shared key and compares. If same, real watcher else reject.
    - Otherwise it waits for an additional Y block.
    - If webhook sent and server is down wait before retrying again.

#### `pay(Invoice memory invoice, EIP712Domain memory domain, bytes memory signature)`

Initialisation:

- Contract stores address of signer `signer`
- mapping(bytes32 ⇒ bool) invoicePaid
- `pay` is called by the buyer
    - msg.sender = buyer address
    - buyer token gets moved
    - buyer pays gas
- `ecrecover(bytes32 messageHash, uint8 v, bytes32 r, bytes32 s)` to recover signer address

Parameter:

- `Struct Invoice`
    - `bytes32 id` : the invoice id that is generated awaiting to be paid
    - `address tokenAddr`: address of the token being used for the transaction
    - `uint amount`: amount of token being asked for
    - `address merchantAddr`: Seller wallet address (where money goes)
    - `uint deadline` : Expiry date for the signature.
        - A price change in the underlying token means buyer will get this for cheaper.
- `Struct EIP712Domain`
    - `string name` : Name of our app
    - `string version` : Version number
    - `uint256 chainId` : Chain signature is agreed upon
    - `address verifyingContract` : Who verified the terms
- `bytes signature`: Signature signed by the signer address. Generated by signing the digest
    - `signature` = signed(`digest`) where `digest` is computed by:
        
        ```python
        structHash      = hash(invoiceId, token, amount, merchant, deadline)   
        domainSeparator = hash(name, version, chainId, contractAddress) 
        digest = keccak256("\x19\x01" + domainSeparator + structHash)
        return digest
        ```
        
    - More on how this is computed: https://eips.ethereum.org/EIPS/eip-71

How it works:

1. Check if `invoicePaid(invoiceId)` has been paid
2. Compute `s`, `r` , `v` from signature
3. Calls `recoveredSigner = ecrecover(digest, s, r, v);` to retrieve signer address
    - What we’re trying to check here is:
        1. Our server did agreed to the following statment:
            “Invoice `*invoiceId` for `amount` of `token` paid to `merchantAddr`valid until `deadline`*.”
        2. As a concrete check, valid signature will return the signer address so we check against our current signer.
    - Any tampering with any terms results in an invalid `ecrecover` which will return a zero address so we check `recoveredSigner != address(0)`
    - Finally, we check if recovered address matches server address `recoveredSigner == signer`
4. Check whether signature has expired `deadline < block.timestamp`
5. TransferFrom
6. Emit `event PaymentReceived(bytes32 indexed invoiceId)` event

## Watcher

### Checking for confirmed transaction

We initiate a websocket connection and subscribe to every new block that is mined.
For every new block:
1. Check for reorg otherwise continue.
2. Check bloom logs if our `PaymentReceived` exists otherwise skip.
3. Grab logs from the block filtered by our contract address. Each logs will contain two topics
    - topic0 represents the hash of `PaymentReceived` event
    - topic1 represents the invoice id
4. Write to database the invoice id, block number and hash.
5. Find all invoice payments that can be marked finalised by finding all invoice payments where the difference between the block it lives on and current block number is greater than threshold.
6. Update finalised payments invoice as confirmed (+ order.confirmed webhook).
7. Mark block as processed.

### Handling Reorg

Reorg happens when two blocks compete with each other and one containing our transaction gets dropped.

Once there's been a reorg we should prevent the merchant from handling the order too early.

We send a `payment.reversed` webhook to the merchant's server then polls the network again waiting for our transaction to be picked up by a block again.

We can guarantee at some point that our transaction will reach finality based on the PoS consensus mechanism.


[^1]: Single slot finality (July 23, 2026) https://ethereum.org/roadmap/single-slot-finality/
[^2]: Faster Than Finality: Comparing Ethereum’s Emerging Confirmation Guarantees (Sep 28, 2026) https://www.ledger.com/academy/series/n3xt/research-ethereum-preconfirmations-vs-finality
[^3]: Towards Single Slot Finality: Evaluating Consensus Mechanisms and Methods for Faster Ethereum Finality (2026) https://arxiv.org/abs/2406.09420
