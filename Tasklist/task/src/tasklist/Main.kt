package tasklist

import java.util.Scanner
import kotlinx.datetime.*
import java.lang.Exception
import java.lang.IllegalArgumentException
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.BufferedSource
import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

enum class AppState() {CONTINUE, END}
enum class TaskState() {FULL, EMPTY}
val priorityColorScheme = mapOf<String, String>(
    "C" to "\u001B[101m \u001B[0m",
    "H" to "\u001B[103m \u001B[0m",
    "N" to "\u001B[102m \u001B[0m",
    "L" to "\u001B[104m \u001B[0m")
val dueTagColorScheme = mapOf<String, String>(
    "I" to "\u001B[102m \u001B[0m",
    "T" to "\u001B[103m \u001B[0m",
    "O" to "\u001B[101m \u001B[0m")
const val separator = "+----+------------+-------+---+---+--------------------------------------------+"
val header = "| N  |    Date    | Time  | P | D |" + " ".repeat(19) + "Task" + " ".repeat(21) + "|"
const val SIZE_BUFFER = 44
const val JSON_FILE = "tasklist.json"

class Task() {
    var priority: String = "L"
        set(value) {
            val v = value.uppercase()
            if (v !in setOf<String>("C", "H", "N", "L")) throw IllegalArgumentException()
            field = v
        }
    var date: String = ""
        set(value) {
            try {
                val parts = value.split("-").map { it.toInt() }
                val string = "%d-%02d-%02d".format(parts[0],parts[1],parts[2])
                Instant.parse(string+"T00:00:00Z")
                field = string
            } catch (e: Exception) {
                println("The input date is invalid")
                throw IllegalArgumentException()
            }
        }
    var time: String = ""
        set(value) {
            try {
                val parts = value.split(":").map { it.toInt() }
                if (parts[0] in 0..23 && parts[1] in 0..59) {
                    field = "%02d:%02d".format(parts[0],parts[1])
                } else {
                    throw Exception()
                }
            } catch (e: Exception) {
                println("The input time is invalid")
                throw IllegalArgumentException()
            }

        }
    var content: String = ""
    var dueTag: String = "I"

    fun updateDueTag() {
        val (y, m, d) = date.split("-").map { it.toInt() }
        val taskDate = LocalDate(y, m, d)
        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val numberOfDays = currentDate.daysUntil(taskDate)
        dueTag = if (numberOfDays == 0) {
            "T"
        } else if (numberOfDays > 0) {
            "I"
        } else "O"
    }
}




class TaskApp() {
    private val tasks = mutableListOf<Task>()
    private var stateApp = AppState.CONTINUE

    fun initializeData() {
        val file = File(JSON_FILE)
        if (file.exists()) {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val type = Types.newParameterizedType(MutableList::class.java, Task::class.java)
            val taskMutableListAdapter = moshi.adapter<MutableList<Task>>(type)
            tasks.addAll(taskMutableListAdapter.fromJson(file.bufferedReader().readText())!!)
        }
    }

    fun mainMenu() {
        initializeData()
        while (true) {
            println("Input an action (add, print, edit, delete, end):")
            val action = readln()
            stateApp = when (action) {
                "add" -> addTask()
                "print" -> printTasks()
                "delete" -> deleteTask()
                "edit" -> editTask()
                "end" -> endApp()
                else -> {
                    println("The input action is invalid")
                    AppState.CONTINUE
                }
            }
            if (stateApp == AppState.END) break
        }
        saveTaskList()
    }

    private fun askPriority(task: Task, scanner: Scanner) {
        while (true) {
            println("Input the task priority (C, H, N, L):")
            try {
                val priority = scanner.nextLine()
                task.priority = priority
                break
            } catch (_: Exception) { }
        }
    }

    private fun askDate(task: Task, scanner: Scanner) {
        while (true) {
            println("Input the date (yyyy-mm-dd):")
            try {
                val date = scanner.nextLine()
                task.date = date
                break
            } catch (_: Exception) { }
        }
    }

    private fun askTime(task: Task, scanner: Scanner) {
        while (true) {
            println("Input the time (hh:mm):")
            try {
                val time = scanner.nextLine()
                task.time = time
                break
            } catch (_: Exception) { }
        }
    }

    private fun askContent(task: Task, scanner: Scanner): TaskState {
        println("Input a new task (enter a blank line to end):")

        var content: String = ""
        while (true) {
            val line = scanner.nextLine().trim()
            if (line.isBlank()) break
            content += "$line\n"
        }
        if (content.isBlank()) {
            println("The task is blank")
            return TaskState.EMPTY
        }
        //content = content.dropLast(3) //remove last 3 blank spaces
        task.content = content
        return TaskState.FULL
    }



    private fun addTask(): AppState {
        val task = Task()
        val scanner = Scanner(System.`in`)
        // Set Priority
        askPriority(task, scanner)
        // Set Date
        askDate(task, scanner)

        // Set Time
        askTime(task, scanner)

        // Set content
        val taskState = askContent(task, scanner)

        if (taskState == TaskState.EMPTY) return AppState.CONTINUE
        tasks.add(task)
        return AppState.CONTINUE
    }

    private fun fromStringToLines(content: String): MutableList<String> {
        fun fillBuffer(buffer: String): String {
            var result = buffer
            val n = SIZE_BUFFER - buffer.length
            if (n > 0) {
                result += " ".repeat(n)
            }
            return result
        }
        val lines = mutableListOf<String>()
        var buffer = ""
        for (i in content) {
            if (i == '\n' && buffer != "") {
                buffer = fillBuffer(buffer)
            } else {
                buffer += i
            }
            if (buffer.length == SIZE_BUFFER) {
                lines.add(buffer)
                buffer = ""
            }
        }
        return lines
    }

    private fun printTaskAsBox(index: Int, task: Task) {
        val lines = fromStringToLines(task.content)
        // First Line apart from content
        for (i in lines.indices) {
            if (i == 0) {
                val initString = "| %-2d | ".format(index)
                print(initString + "${task.date} | ${task.time} | ${priorityColorScheme[task.priority]} |" +
                        " ${dueTagColorScheme[task.dueTag]} |")
            } else {
                print("|    |            |       |   |   |")
            }
            println(lines[i] + "|")
        }
        println(separator)
    }

    private fun printTasks(): AppState {
        if (tasks.isEmpty()) {
            println("No tasks have been input")
            return AppState.CONTINUE
        }
        println(separator)
        println(header)
        println(separator)
        tasks.forEachIndexed { i, t ->
            t.updateDueTag()
            printTaskAsBox(i+1, t)
        }
        println()
        return AppState.CONTINUE
    }

    private fun endApp(): AppState {
        println("Tasklist exiting!")
        return AppState.END
    }

    private fun deleteTask(): AppState {
        if (tasks.isEmpty()) {
            println("No tasks have been input")
            return AppState.CONTINUE
        }

        printTasks()
        while (true) {
            println("Input the task number (1-${tasks.size}):")
            var index = 0
            try {
                index = readln().toInt()
                if (index !in 1..tasks.size) {
                    throw IllegalArgumentException()
                }
            } catch (e: Exception) {
                println("Invalid task number")
                continue
            }

            tasks.removeAt(index - 1)
            println("The task is deleted")
            break
        }
        return AppState.CONTINUE
    }

    private fun editTask(): AppState {
        if (tasks.isEmpty()) {
            println("No tasks have been input")
            return AppState.CONTINUE
        }

        printTasks()
        while (true) {
            println("Input the task number (1-${tasks.size}):")
            var index = 0
            try {
                index = readln().toInt()
                if (index !in 1..tasks.size) {
                    throw IllegalArgumentException()
                }
            } catch (e: Exception) {
                println("Invalid task number")
                continue
            }

            val scanner = Scanner(System.`in`)
            while (true) {
                println("Input a field to edit (priority, date, time, task):")
                when (readln()) {
                    "priority" -> {
                        askPriority(tasks[index - 1], scanner)
                        break
                    }

                    "date" -> {
                        askDate(tasks[index - 1], scanner)
                        break
                    }

                    "time" -> {
                        askTime(tasks[index - 1], scanner)
                        break
                    }

                    "task" -> {
                        askContent(tasks[index - 1], scanner)
                        break
                    }
                    else -> println("Invalid field")
                }
            }

            println("The task is changed")

            break
        }
        return AppState.CONTINUE
    }

    fun saveTaskList() {
        val jsonFile = File(JSON_FILE)
        jsonFile.createNewFile()
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val type = Types.newParameterizedType(MutableList::class.java, Task::class.java)
        val taskMutableListAdapter = moshi.adapter<MutableList<Task>>(type)
        val content = taskMutableListAdapter.toJson(tasks)
        jsonFile.bufferedWriter().use {
            it.write(content)
        }
    }

}

fun main() {
    // write your code here
    val app = TaskApp()
    app.mainMenu()
}


