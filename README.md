# Samiz

[![GitHub downloads](https://img.shields.io/github/downloads/KoalaSat/samiz/total?label=Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/KoalaSat/samiz/releases)
[![release](https://img.shields.io/github/v/release/KoalaSat/samiz)](https://github.com/KoalaSat/samiz/releases)
[![MIT](https://img.shields.io/badge/license-MIT-blue)](https://github.com/KoalaSat/samiz/blob/main/LICENSE)
[![Nostr](https://img.shields.io/badge/chat-nostr-brightgreen)](https://chachi.chat/groups.0xchat.com/Ofar1AYHvG12Y1jx)

Create a Bluetooth mesh with your Android device and use Nostr without accessing the internet.

<div align="center">
    <img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Description of Image" />
</div>
<div align="center">
    <a href="https://github.com/ImranR98/Obtainium" target="_blank">
        <img src="./docs/obtainium.png" alt="Get it on Obtaininum" height="70" />
    </a>    
    <a href="https://github.com/zapstore/zapstore/releases" target="_blank">
        <img src="./docs/zapstore.svg" alt="Get it on Zap.Store" height="70" />
    </a>
    <a href="https://github.com/KoalaSat/samiz/releases" target="_blank">
        <img src="https://github.com/machiav3lli/oandbackupx/raw/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="70">
    </a>
</div>

## Community videos

1. Offline mesh spread

https://github-production-user-asset-6210df.s3.amazonaws.com/111684255/457815617-278a7054-8962-4679-a1b8-0c167d0bf39d.mp4

## Use cases
### Case A
Alice sits in her garden and wants to post an update about her thoughts using a nostr note. However, Alice's internet connection is currently down, so she is unable to share her thoughts with her network:

<div align="center">
<img src="https://github.com/user-attachments/assets/9f2e8179-d2af-423a-bde9-ce26e979e16c" width="500"/>
</div>

1. Alice initiates a new session with Samiz and creates the nostr note with her favorite nostr client.
2. The nostr client shares the note with all apps listening on her device, and also registers it on a local relay.
3. Samiz detects the note through the local relay and stores it in the running session.
4. Bob happens to be near Alice's house. He also has an open session with Samiz, so their devices automatically connect and synchronize, allowing Bob's device to store and access Alice's note.
5. As Bob sits near Charlie on the metro, who is also running a Samiz session.
6. Their apps automatically synchronizes and results on Charlie's device now storing and being able to access Alice's note.

<div align="center">
<img src="https://github.com/user-attachments/assets/e9fb7b4c-2a0e-43bb-8285-16877736118d" width="500"/>
</div>

### Case B
The town is in the midst of a lively festival, with everyone out on the streets. However, a technical issue has caused a town-wide internet outage. Alice, fresh from baking a batch of cookies, wants to spread the word to the festival-goers as quickly as possible:

<div align="center">
<img src="https://github.com/user-attachments/assets/fb7ae48a-ac99-4a4f-a158-803f0e1641f3" width="500"/>
</div>

1. Alice initiates a new session with Samiz and creates the nostr note with her favorite nostr client.
2. The nostr client shares the note with all apps listening on her device, and also registers it on a local relay.
3. Samiz detects the note through the local relay and stores it in the running session.
4. Bob and Charlie, who are also running Samiz sessions, happen to be near Alice, so their devices automatically synchronize and receive Alice's nostr event.
5. Frank and Eve, who are also running Samiz sessions, are far from Alice but in close proximity to Bob.
6. After synchronizing with Alice, Bob's session automatically shares the note with Frank and Eve's sessions, allowing them to receive and read Alice's note.

<div align="center">
<img src="https://github.com/user-attachments/assets/2aa39fd8-e3ea-4a1c-999f-836b62b61d3f" width="500"/>
</div>

### Case C
As the festival continues, everyone is having a great time and starting to capture the moment. They're using Samiz to share updates and notes with each other in the background. However, the town's internet connection remains down due to technical issues, meaning that news of the event is not reaching anyone outside of the town.

<div align="center">
<img src="https://github.com/user-attachments/assets/8adfcb16-1343-42be-bf35-fdc152b136c5" width="500"/>
</div>

Frank decides to leave the party a bit early to pay a visit to Faythe, a tech-savvy individual who has a reliable satellite internet connection.

1. Faythe is running a Samiz session.
2. As soon as Frank arrives at the house, their devices automatically synchronize and Faythe receives all the nostr notes collected by Bob while he was at the party.
3. Once the synchronization is complete, and because Faythe is connected to the internet, his Samiz app begins publishing the notes to his preferred nostr relays.
4. Mike, who lives in another country, can now access all the notes about the party published by Faythe through the nostr relays.

<div align="center">
<img src="https://github.com/user-attachments/assets/15b0cc28-6e41-4049-98a4-ff32fd48ab24" width="500"/>
</div>

# Docs

## Event Flow

![Screenshot from 2025-06-23 11-03-57](https://github.com/user-attachments/assets/aacf1d23-3049-4001-b5b7-fa8f969a7af8)

Samiz is just the communication layer between local relays, that means that in order to have a full experience you'll require to install your own local relay and use it with your own nostr client.


## BLE Messaging

Samiz utilizes BLE technology to ensure low battery consumption. 

BLE operates on a client-server model, so the first step for the two paired devices is to establish their roles. To do this, each device generates a random UUID and shares it as part of the service data.

```
SERVER UUID > CLIENT UUID
```

Because of the limitations of this technology, achieving HTTP-like behavior requires multiple key considerations:

<div align="center">
    <img src="https://github.com/user-attachments/assets/728a6cf7-992f-4a1f-8aa8-cdb3cff85c48" width="600"/>
</div>

- *compressByteArray*: To minimize message size, the string is converted to a ByteArray and then compressed using Deflater.
- *splitInChunks*: BLE has a 512-byte limit per message. For larger messages, the text is split into chunks and sent individually to the other device. To facilitate this process, each chunk includes metadata:

````
  [chunk index (int)][chunk][is last chunk (boolean)]
````

This metadata includes the chunk's index in the original message and a boolean indicating whether it's the final chunk.

- *joinChunks*: Once all chunks are received, the message is reassembled.
- *decompressByteArray*: The compressed ByteArray is then decompressed, allowing it to be converted back to a String.

The same behavior also applies to writing.

## Negentropy

Samiz uses BLE technology and [Negentropy](https://github.com/hoytech/strfry/blob/542552ab0f5234f808c52c21772b34f6f07bec65/docs/negentropy.md) to achieve lower battery consumption and maximum efficiency for device synchronization.

<div align="center">
    <img src="https://github.com/user-attachments/assets/3b7c1fc6-fd7f-49af-972f-496b23f02429" width="600"/>
</div>

## Mesh spread

While two devices are fully synchronized and connected, they will continue to broadcast new events, whether generated by the device itself or received through other connections.

### Client Broadcasting

Clients will send events as write messages, similar to the process used during Negentropy synchronization.

### Server Broadcasting

Servers will send an empty notification to all connected clients. Once a client receives the notification, it will proceed to receive the event as a read message.

# Sponsors

<div align="center">
    <a href="https://opensats.org" target="_blank">
        <img src="./docs/opensats_logo.png" alt="Get it on Obtaininum" />
    </a>
</div>
