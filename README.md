# Samiz

## Use cases
### Case A
Alice sits in her garden and wants to share with everyone how great everything is going with a nostr note. Unfortunately, Alice's internet connection is facing technical difficulties, so she cannot share her thoughts with her network:
<div align="center">
<img src="https://github.com/user-attachments/assets/9f2e8179-d2af-423a-bde9-ce26e979e16c" width="500"/>
</div>

1. Alice starts a new session with Samiz and creates the nostr note with her favorite nostr client.
2. The nostr client shares the note with all apps listening on her device and/or register it on a local relay.
3. Samiz detects the note and stores it in the running session
4. Bob is passing by close to Alice's house. He also has an open session with Samiz, so both devices automatically connect and synchronize, resulting in Bob's device now storing and being able to access Alice's note
5. Bob goes to the metro and sits close to Charlie, who is also running a Samiz session.
6. Their apps automatically synchronizes and results on Charlie's device now storing and being able to access Alice's note.

<div align="center">
<img src="https://github.com/user-attachments/assets/e9fb7b4c-2a0e-43bb-8285-16877736118d" width="500"/>
</div>

### Case B
It's a local festivity, and everybody is on the streets celebrating. Unfortunately, for technical reasons, the internet connection is down in the whole town. Alice, who just made cookies, wants to let everyone know as quickly as possible:

<div align="center">
<img src="https://github.com/user-attachments/assets/fb7ae48a-ac99-4a4f-a158-803f0e1641f3" width="500"/>
</div>

1. Alice starts a new session with Samiz and creates a nostr note with her favorite nostr client.
2. The nostr client shares the note with all apps listening on her device and/or register it on a local relay.
3. Samiz detects the note and stores it in the running session
4. Bob and Charlie are close to Alice with a running Samiz session, so their devices automatically synchronize and receive Alice's nostr event.
5. Frank and Eve, who are also running a Samiz session, are far from Alice but close to Bob.
6. After Bob synchronized with Alice, his session automatically synchronizes with Frank and Eve's, allowing them to receive and read Alice's note.

<div align="center">
<img src="https://github.com/user-attachments/assets/2aa39fd8-e3ea-4a1c-999f-836b62b61d3f" width="500"/>
</div>

### Case C
Following example B, everybody is enjoying and starting to document the event, using Samiz on the background to share notes to each other. Unfortunately, the internet connection is still facing technical difficulties and no one outside the town can hear about it.

<div align="center">
<img src="https://github.com/user-attachments/assets/8adfcb16-1343-42be-bf35-fdc152b136c5" width="500"/>
</div>

Frank leaves the party earlier to visit Faythe, a tech enthusiast, who has satellite internet connection:

1. Faythe is running a Samiz session.
2. The moment Frank enters the house, their apps automatically start synchronizing and Faythe receives all the nostr notes received by Bob while he was at the party.
3. After the synchronization, and because Faythe is connected to the internet, his Samiz app starts publishing all notes to Faythe's favorite relays.
4. Mike, who lives in another country, can now have access to all the notes published by Faythe.

<div align="center">
<img src="https://github.com/user-attachments/assets/15b0cc28-6e41-4049-98a4-ff32fd48ab24" width="500"/>
</div>
