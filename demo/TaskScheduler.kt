package demo

data class Task(
    val id: String,
    val title: String,
    val priority: Int,
    val done: Boolean = false,
)

class TaskScheduler {

    private val tasks = mutableListOf<Task>()

    fun add(title: String, priority: Int = 0): Task {
        val task = Task(id = generateId(), title = title, priority = priority)
        tasks += task
        return task
    }

    fun complete(id: String): Boolean {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return false
        tasks[index] = tasks[index].copy(done = true)
        return true
    }

    fun pending(): List<Task> =
        tasks.filter { !it.done }

    fun next(): Task? =
        pending().maxByOrNull { it.priority }

    fun summary(): String {
        val done  = tasks.count { it.done }
        val total = tasks.size
        return "$done / $total tasks completed"
    }

    // Returns all tasks sorted by priority, highest first
    fun sorted(): List<Task> =
        tasks.sortedByDescending { it.priority }

    // Deprecated — no active callers; scheduled for removal
    fun findById(id: String): Task? =
        tasks.find { it.id == id }

    private fun generateId(): String =
        (1..8).map { ('a'..'z').random() }.joinToString("")

}
