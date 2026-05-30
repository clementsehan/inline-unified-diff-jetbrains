# Demo script — TaskScheduler.kt

Open `TaskScheduler.kt`, commit it as the baseline, then apply the edits below
one at a time to produce each diff type.  Toggle the inline diff on between
each step to record a clean gif frame.

```
git add demo/TaskScheduler.kt
git commit -m "chore: add demo baseline"
```

---

## Scene 1 — Modified lines (unsafe)

Change the `summary()` return value so the diff shows a red ghost + green replacement:

```diff
-        return "$done / $total tasks completed"
+        return "Progress: $done done, ${total - done} remaining"
```

**What the plugin shows:** red ghost block with the old string immediately above
the green-highlighted new line.  No safe label — this is an unsafe change.

---

## Scene 2 — Added lines (pure green)

Add a `clear()` function after `sorted()`:

```diff
+    fun clear() {
+        tasks.clear()
+    }
+
```

**What the plugin shows:** three consecutive green-highlighted lines with no
ghost block above them.

---

## Scene 3 — Deleted lines (red ghost only)

Remove the `sorted()` function entirely:

```diff
-    // Returns all tasks sorted by priority, highest first
-    fun sorted(): List<Task> =
-        tasks.sortedByDescending { it.priority }
-
```

**What the plugin shows:** a red ghost block at the deletion site; no green
lines below it.

---

## Scene 4 — Safe: comment-only change

Edit the comment above `sorted()` without touching any code:

```diff
-    // Returns all tasks sorted by priority, highest first
+    // Returns tasks ordered from highest to lowest priority
```

**What the plugin shows:** the chunk is labelled **✓ Safe: Comments modified**
in blue above the green line — safe to keep without review.

---

## Scene 5 — Safe: dead code removal

Remove the `findById` function (it has no callers in the project):

```diff
-    // Deprecated — no active callers; scheduled for removal
-    fun findById(id: String): Task? =
-        tasks.find { it.id == id }
-
```

**What the plugin shows:** the ghost block carries the label
**✓ Safe: Dead code removed** — the plugin's PSI search confirmed zero
call-sites for `findById` in the index.

---

## Scene 6 — Bulk "Accept safe" demo

Apply scenes 4 and 5 together (comment edit + dead code removal).  Open the
floating summary panel and click **✓ Accept safe** — both safe chunks are
dismissed at once, leaving only unsafe chunks for manual review.

---

## Reset between scenes

```
git restore demo/TaskScheduler.kt
```
