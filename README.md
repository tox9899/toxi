# TOXIAuth 1.0.0

A starter Paper login/register plugin. Players use `/register <password> <confirm-password>` once, then `/login <password>` on later joins. Passwords are stored as BCrypt hashes, not plaintext.

## Compatibility
- Built against Paper API 1.21.4 and Java 21.
- Intended for Paper servers running Java 21.
- Geyser players can send the same commands from Bedrock chat.
- Floodgate is recommended for Bedrock access, but this plugin does not require a direct Floodgate API dependency. It keys accounts by the UUID Paper provides for the player.
- This is a command-based first version, not a Bedrock form UI.

## Build on Windows
1. Install JDK 21.
2. Install Apache Maven.
3. Extract the project folder.
4. Open Command Prompt in the folder containing `pom.xml`.
5. Run: `mvn clean package`
6. The plugin JAR will be at `target/TOXIAuth-1.0.0.jar`.

## Install on Aternos
1. Back up the server first.
2. Build the JAR locally.
3. Check whether Aternos offers a way to upload custom plugins on your plan/server software. Aternos generally restricts arbitrary plugin JAR uploads; if there is no upload option, this custom JAR cannot be installed directly there.
4. If available, place the JAR in `plugins/`, restart, and check the console for `TOXIAuth enabled`.
5. On first run, the plugin creates `plugins/TOXIAuth/config.yml` and `plugins/TOXIAuth/accounts.yml`.

## Player commands
- `/register <password> <confirm-password>`
- `/login <password>`
- `/changepassword <old-password> <new-password>`

Passwords must be 8–64 characters by default. Change this in `config.yml`.

## Important limitations before public use
This is a functional starter, not a fully hardened production authentication system:
- It does not include a GUI/form.
- It does not yet implement login timeouts, IP/session controls, brute-force rate limits, email recovery, or database backups.
- It does not protect every possible plugin-specific interaction or proxy configuration.
- If using another authentication plugin, do not run both systems simultaneously.
- Test with a private test account before inviting players. Keep regular server backups.

Never ask players to send passwords to admins. Do not reuse passwords from other services.
