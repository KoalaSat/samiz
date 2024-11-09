# Samiz

## Use cases
### Case A
Alice sits in her garden and wants to share with everyone how great everything is going with a nostr note. Unfortunately, Alice's internet connection is facing technical difficulties, so she cannot share her thoughts with her network.

1. Alice starts a new session with Samiz and creates the nostr note with her favorite nostr client.
2. The nostr client shares the note with all apps listening on her device.
3. Samiz detects it and stores it in the running session
4. Bob is passing by close to Alice's house. He also has an open session with Samiz, so both devices connect and synchronize, resulting in Bob's device now storing Alice's note
5. Bob goes to the metro and sits close to Charlie, who is also running a Samiz session.
7. Their apps synchronizes and he Charlie receives Alice's note so he can also have access to it.


### Case B
It's a local festivity, and everybody is on the streets celebrating. Unfortunately, for technical reasons, the internet connection is down in the whole town. Alice, who just made cookies, wants to let everyone know as quickly as possible to come where she is an try, while the cookies are still warm.

1. Alice starts a new session with Samiz and creates a nostr note with her favorite nostr client.
2. The nostr client shares the note with all apps listening on her device
3. Samiz detects it and stores it in the running session
4. Bob and Charlie are close to Alice with a running Samiz session, so their devices synchronize and receive Alice's nostr event.
5. Frank and Eve, who are also running a Samiz session, are far from Alice but close to Bob.
6. After Bob synchronizes with Alice, his session synchronizes with Frank and Eve's, allowing them to receive Alice's note.


### Case C
Following example B, everybody is enjoying Alice's cookies and starting to document their fantastic taste. Unfortunately, the internet connection is still facing technical difficulties, so no one outside the town can hear about Alice's cookies. However, Faythe, who is a tech enthusiast, has a satellite internet connection. Frank, who leaves the party earlier, visits Faythe.

1. Faythe is running a Samiz session. The moment Frank passes through the door, their apps start synchronizing, and Faythe receives all the notes everyone at the party shared about Alice's cookies.
2. After the synchronization, and because Faythe is connected to the internet, his Samiz app starts publishing all notes to Faythe's favorite relays.
3. Mike, who lives in another town, can now have access to all the notes created by the participants at the party.
