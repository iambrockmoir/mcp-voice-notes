# Security Policy

## 🔒 API Key Protection

This project uses sensitive API keys that **MUST NEVER** be committed to version control.

### Protected Files

The following files contain API keys and are already in `.gitignore`:

```
.env                              # Main environment variables
voice_notes_mcp/.env             # MCP server configuration
android_app/local.properties     # Android build secrets
claude_desktop_config.json       # Claude Desktop keys
claude_settings.json             # Claude Code keys
```

### ✅ Safe Files (Templates)

These template files are safe to commit and should be used as references:

- `.env.example` - Template for environment variables
- `android_app/local.properties.example` - Template for Android secrets
- `claude_desktop_config.example.json` - Template for Claude Desktop
- `claude_settings.example.json` - Template for Claude Code

## 🚨 Before Your First Commit

**CRITICAL CHECKLIST:**

1. ✅ Verify `.gitignore` is in place
2. ✅ Check no `.env` files are staged:
   ```bash
   git status
   ```
3. ✅ Confirm API key files are ignored:
   ```bash
   git check-ignore -v .env voice_notes_mcp/.env android_app/local.properties
   ```

## 🔑 API Keys Used in This Project

### 1. Supabase Credentials
- **Service Role Key** (🔴 CRITICAL - Server-side only)
  - Used in: `voice_notes_mcp/.env`, `claude_desktop_config.json`, `claude_settings.json`
  - Access: Full database access
  - ⚠️ **NEVER use in Android app or client code**

- **Anon Key** (🟡 Public but sensitive)
  - Used in: `android_app/local.properties`
  - Access: Row Level Security restricted
  - Can be exposed in client apps but prefer environment injection

### 2. OpenAI API Key
- Used in: `.env`, `android_app/local.properties`
- Access: Costs money per API call
- ⚠️ Keep secure, monitor usage

### 3. Supabase URL
- Used in: All configuration files
- Not as sensitive but prefer keeping private

## 🛡️ Security Best Practices

### For Local Development

1. **Create your `.env` from template:**
   ```bash
   cp .env.example .env
   # Edit .env with your actual keys
   ```

2. **Never share `.env` files:**
   - Don't send via email/Slack/Discord
   - Don't screenshot with keys visible
   - Don't paste into AI chats

3. **Use environment variables:**
   ```bash
   # Good
   export SUPABASE_KEY=$(cat .env | grep SUPABASE_SERVICE_ROLE_KEY | cut -d '=' -f2)

   # Bad
   SUPABASE_KEY="eyJhbGciOiJIUzI1NiIsInR..." # hardcoded
   ```

### For Production Deployment

1. **Use secret management:**
   - GitHub Secrets for Actions
   - Railway/Vercel environment variables
   - AWS Secrets Manager
   - HashiCorp Vault

2. **Rotate keys regularly:**
   - Every 90 days minimum
   - Immediately if compromised
   - After team member changes

3. **Enable Supabase RLS:**
   ```sql
   ALTER TABLE notes ENABLE ROW LEVEL SECURITY;

   CREATE POLICY "Users can only access their notes"
   ON notes FOR ALL
   USING (auth.uid() = user_id);
   ```

### For Android App

1. **Use BuildConfig injection:**
   ```kotlin
   // Already implemented in build.gradle.kts
   val supabaseUrl = BuildConfig.SUPABASE_URL
   val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
   ```

2. **Never hardcode in source:**
   ```kotlin
   // ❌ BAD
   val key = "eyJhbGciOiJIUzI1NiIsInR..."

   // ✅ GOOD
   val key = BuildConfig.SUPABASE_ANON_KEY
   ```

## 🔍 What to Do If Keys Are Exposed

### If you accidentally committed API keys:

1. **Immediately rotate the keys:**
   - Supabase: Project Settings → API → Generate new keys
   - OpenAI: Revoke and create new API key

2. **Remove from Git history:**
   ```bash
   # Use BFG Repo-Cleaner or git-filter-repo
   git filter-repo --invert-paths --path .env
   ```

3. **Force push cleaned history:**
   ```bash
   git push origin --force --all
   ```

4. **Notify team members to re-clone**

### If keys are leaked publicly:

1. **Rotate immediately** (within minutes)
2. **Check for unauthorized usage:**
   - Supabase Dashboard → Logs
   - OpenAI Usage page
3. **Enable additional security:**
   - IP restrictions if available
   - Rate limiting
   - Audit logging

## 📋 Security Checklist for Contributors

Before submitting a PR:

- [ ] No hardcoded API keys in code
- [ ] All secrets use environment variables
- [ ] New secrets added to `.gitignore`
- [ ] Templates (`.example` files) updated
- [ ] No screenshots containing keys
- [ ] `git log -p` shows no keys in diff

## 🤝 Reporting Security Issues

If you discover a security vulnerability:

1. **DO NOT** open a public issue
2. Email: [Add your security contact email]
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## 📚 Additional Resources

- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
- [GitHub Secret Scanning](https://docs.github.com/en/code-security/secret-scanning)
- [Supabase Security Best Practices](https://supabase.com/docs/guides/auth)
- [Android Security Tips](https://developer.android.com/privacy-and-security/security-tips)

---

**Remember:** Security is everyone's responsibility. When in doubt, ask before committing!