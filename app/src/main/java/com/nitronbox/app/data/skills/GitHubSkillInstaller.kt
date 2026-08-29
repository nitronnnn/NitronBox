package com.nitronbox.app.data.skills

import com.nitronbox.app.data.settings.Skill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Installs skills from GitHub. Accepts:
 *  - https://github.com/user/repo            (root SKILL.md on the default branch)
 *  - https://github.com/user/repo/tree/branch/path
 *  - https://raw.githubusercontent.com/...   (any raw file URL)
 * The fetched markdown becomes the skill prompt; a YAML-ish `name:` frontmatter line
 * names the skill, otherwise the file/repo name is used.
 */
class GitHubSkillInstaller(private val client: OkHttpClient) {

    suspend fun install(input: String): Skill = withContext(Dispatchers.IO) {
        val trimmed = input.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "Empty URL" }

        val rawUrls = rawCandidates(trimmed)
        var lastError: String? = null
        for (url in rawUrls) {
            val text = fetch(url)
            if (text != null) {
                val name = url.substringBeforeLast('/')
                    .substringAfterLast('/')
                    .substringAfterLast('.')
                    .ifBlank { "skill" }
                return@withContext parseSkill(text, fallbackName = name)
            }
            lastError = url
        }
        throw IllegalStateException("Nothing found at $lastError")
    }

    private fun rawCandidates(input: String): List<String> {
        // Already a raw URL.
        if ("raw.githubusercontent.com" in input) return listOf(input)
        // github.com/user/repo(/tree/branch(/path...))
        val m = GITHUB_REPO.matchEntire(input.removePrefix("https://").removePrefix("http://")) ?: return listOf(input)
        val (user, repo, rest) = m.destructured
        val branches = listOf("main", "master")
        val path = rest.split("/").filter { it.isNotBlank() }
        return buildList {
            if (path.isEmpty()) {
                branches.forEach { b -> add("https://raw.githubusercontent.com/$user/$repo/$b/SKILL.md") }
                branches.forEach { b -> add("https://raw.githubusercontent.com/$user/$repo/$b/README.md") }
            } else {
                // path may start with the branch: tree/branch/rest or branch/rest
                val afterTree = if (path.first() == "tree") path.drop(1) else path
                val branch = afterTree.firstOrNull() ?: "main"
                val restPath = afterTree.drop(1)
                val base = "https://raw.githubusercontent.com/$user/$repo/$branch"
                if (restPath.isEmpty()) {
                    add("$base/SKILL.md")
                    add("$base/README.md")
                } else {
                    val p = restPath.joinToString("/")
                    add("$base/$p")
                    add("$base/$p/SKILL.md")
                    branches.drop(1).forEach { other -> add("https://raw.githubusercontent.com/$user/$repo/$other/$p") }
                }
            }
        }
    }

    private fun fetch(url: String): String? = runCatching {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()

    private fun parseSkill(text: String, fallbackName: String): Skill {
        var name = fallbackName
        var prompt = text
        // Minimal frontmatter: ---\nname: X\n--- ...
        if (text.startsWith("---")) {
            val end = text.indexOf("---", 3)
            if (end > 0) {
                val front = text.substring(3, end)
                prompt = text.substring(end + 3).trim()
                Regex("name\\s*:\\s*(.+)").find(front)?.let { m ->
                    name = m.groupValues[1].trim()
                }
            }
        }
        return Skill(name = name.trim().take(80), prompt = prompt.trim())
    }

    private companion object {
        val GITHUB_REPO = Regex(
            "github\\.com/([^/]+)/([^/]+)(?:/(.*))?",
        )
    }
}
