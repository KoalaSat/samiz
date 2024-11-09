# Samiz

## Use cases
###Case A
Alice sits in her garden and wants to share with everyone how great everything is going with a nostr note. Unfortunately, Alice's internet connection is facing technical difficulties, so she cannot share her happiness with her network.

1. Alice starts a new session with Samiz and creates the nostr note with her favorite nostr client.
2. The nostr client shares the note with all apps listening on her device.
3. Samiz detects it and stores it in the running session
4. Bob is passing by close to Alice's house. He also has an open session with Samiz, so both devices connect and synchronize, resulting in Bob's device now storing Alice's note
5. Bob unblocks his device on the metro and starts reading Alice's note with his favorite nostr client
6. Charlie is also on the same metro wagon and running a Samiz session.
7. His app synchronizes with Bob's, and he receives Alice's note so he can also access it.


### Case B
It's a local festivity, and everybody is on the streets celebrating and having a good time. Unfortunately, for technical reasons, the internet connection is down in the whole town. Alice, who just made cookies, wants to let everyone know as quickly as possible while the cookies are still warm

1. Alice starts a new session with Samiz and creates the nostr note with her favorite nostr client.
2. The nostr client shares the note with all apps listening on her device
3. Samiz detects it and stores it in the running session
4. Bob and Charlie are close to Alice with running Samiz sessions, so their devices synchronize and receive Alice's nostr event.
5. Frank and Eve, who are also running a Samiz session, are far from Alice but close to Bob.
6. After Bob synchronizes with Alice, his session will synchronize with Frank and Eve's, allowing them to receive Alice's note.


### Case C
Following example B, everybody is enjoying Alice's cookies and starting to document their fantastic taste. Unfortunately, the internet connection is still facing technical difficulties, so no one outside the town can learn about Alice's cookies. However, Faythe, who is a tech enthusiast, has a satellite internet connection. Frank leaves the party on his way home to visit Faythe.

1. Faythe is also running a Samiz session. The moment Frank passes through the door, their sessions start synchronizing, and he receives all the notes everyone at the party shared about Alice's cookies from Bob.
2. After the synchronization, and because Faythe is connected to the internet, his Samiz starts publishing all notes to their favorite relays.
3. Mike, who lives in another town, can now read all the notes created by the participants at the party.
