# Quick Start: Generate Slugs for Existing Organizations

## TL;DR

Run this once to generate URL slugs for all existing organizations:

1. **Edit `application.properties`** - Add this line:
   ```properties
   migration.generate-slugs.enabled=true
   ```

2. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

3. **Watch the logs** - You'll see output like:
   ```
   [OK] Generated slug for 'Keter Torah' (ID: O1234567890): 'keter-torah'
   ```

4. **After it completes** - Edit `application.properties` again:
   ```properties
   migration.generate-slugs.enabled=false
   ```

5. **Restart the application** - Done!

## What This Does

Converts organization names to URL-friendly slugs:
- "Keter Torah" → `keter-torah`
- "Congregation Beth Aaron" → `congregation-beth-aaron`
- "Young Israel of Teaneck" → `young-israel-of-teaneck`

Organizations can then be accessed at `/org/keter-torah` instead of `/orgs/O1234567890`.

## Safety

- ✅ **Safe to run multiple times** - Skips orgs that already have slugs
- ✅ **Only runs when enabled** - Won't accidentally run on startup
- ✅ **Detailed logging** - See exactly what happened
- ✅ **Error handling** - One failure won't stop the whole migration

## Need Help?

See the full README in this directory for detailed documentation, troubleshooting, and examples.
