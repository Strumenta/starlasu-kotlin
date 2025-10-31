package com.strumenta.starlasu.lionwebclient

import com.strumenta.starlasu.lionweb.LIONWEB_VERSION_USED_BY_STARLASU
import com.strumenta.starlasu.lionweb.registerSerializersAndDeserializersInMetamodelRegistry
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.SyntheticSource
import com.strumenta.starlasu.model.assignParents
import io.lionweb.client.testing.AbstractClientFunctionalTest
import io.lionweb.kotlin.DefaultMetamodelRegistry
import io.lionweb.kotlin.getChildrenByContainmentName
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals

@Testcontainers
class TodoFunctionalTest : AbstractClientFunctionalTest(LIONWEB_VERSION_USED_BY_STARLASU, true) {
    @Test
    fun noPartitionsOnNewModelRepository() {
        val starlasuClient = StarlasuClient(
            port = server!!.firstMappedPort,
            repository = "repo_noPartitionsOnNewModelRepository"
        )
        starlasuClient.createRepository()
        assertEquals(emptyList(), starlasuClient.getPartitionIDs())
    }

    @Test
    fun storePartitionAndGetItBack() {
        val starlasuClient = StarlasuClient(
            port = server!!.firstMappedPort,
            debug = true,
            repository = "repo_storePartitionAndGetItBack"
        )
        starlasuClient.createRepository()
        starlasuClient.registerLanguage(todoLanguage)
        starlasuClient.registerLanguage(todoAccountLanguage)
        registerSerializersAndDeserializersInMetamodelRegistry()
        DefaultMetamodelRegistry.prepareSerialization(starlasuClient.jsonSerialization)

        assertEquals(emptyList(), starlasuClient.getPartitionIDs())

        // We create an empty partition
        val todoAccount = TodoAccount("my-wonderful-partition")
        val expectedPartitionId = todoAccount.id!!
        // By default, the partition IDs are derived from the source
        // todoAccount.source = SyntheticSource("my-wonderful-partition")
        starlasuClient.createPartition(todoAccount)

        val partitionIDs = starlasuClient.getPartitionIDs()
        assertEquals(listOf(todoAccount.id), partitionIDs)

        // Now we want to attach a tree to the existing partition
        val todoProject =
            TodoProject(
                "My errands list",
                mutableListOf(
                    Todo("Buy milk"),
                    Todo("Take the garbage out"),
                    Todo("Go for a walk")
                )
            )

        todoProject.source = SyntheticSource("TODO Project A")
        todoProject.assignParents()
        val todoProjectID = starlasuClient.attachAST(
            todoProject,
            containerID = expectedPartitionId,
            containmentName = "projects"
        )

        // I can retrieve the entire partition
        val retrievedTodoAccount = starlasuClient.getLionWebNode(expectedPartitionId)
        assertEquals(1, retrievedTodoAccount.getChildrenByContainmentName("projects").size)
        assertEquals(
            listOf(todoProjectID),
            retrievedTodoAccount.getChildrenByContainmentName("projects")
                .map { it.id }
        )

        // I can retrieve just a portion of that partition. In that case the parent of the root of the
        // subtree will appear null
        val expectedProjectId = starlasuClient.idFor(todoProject)
        assertEquals("synthetic_TODO_Project_A", expectedProjectId)
        val retrievedTodoProject = starlasuClient.getAST(expectedProjectId)
        assertEquals(
            TodoProject(
                "My errands list",
                mutableListOf(
                    Todo("Buy milk"),
                    Todo("Take the garbage out"),
                    Todo("Go for a walk")
                )
            ).apply { assignParents() },
            retrievedTodoProject
        )
        assertEquals(null, retrievedTodoProject.parent)
    }

    @Test
    fun checkNodeIDs() {
        val repoName = "checkNodeIDs"
        val starlasuClient = StarlasuClient(port = server!!.firstMappedPort, debug = true, repository = repoName
        )
        starlasuClient.lionWebClient.createRepository(repoName, LIONWEB_VERSION_USED_BY_STARLASU, history = false)
        starlasuClient.registerLanguage(todoLanguage)
        starlasuClient.registerLanguage(todoAccountLanguage)

        assertEquals(emptyList(), starlasuClient.getPartitionIDs())

        // We create an empty partition
        val todoAccount = TodoAccount("my-wonderful-partition")
        // By default the partition IDs are derived from the source
        starlasuClient.createPartition(todoAccount)

        // Now we want to attach a tree to the existing partition
        val todoProject =
            TodoProject(
                "My errands list",
                mutableListOf(
                    Todo("Buy milk"),
                    Todo("Take the garbage out"),
                    Todo("Go for a walk")
                )
            )
        todoProject.assignParents()
        todoProject.source = SyntheticSource("MyProject")
        starlasuClient.attachAST(todoProject, todoAccount, containmentName = "projects")

        // assertEquals("partition_synthetic_my-wonderful-partition", kolasuClient.idFor(todoAccount))
        assertEquals("synthetic_MyProject", starlasuClient.idFor(todoProject))
        assertEquals("synthetic_MyProject_todos", starlasuClient.idFor(todoProject.todos[0]))
        assertEquals("synthetic_MyProject_todos_1", starlasuClient.idFor(todoProject.todos[1]))
        assertEquals("synthetic_MyProject_todos_2", starlasuClient.idFor(todoProject.todos[2]))
    }

    @Test
    fun sourceIsRetrievedCorrectly() {
        val repoName = "sourceIsRetrievedCorrectly"
        val starlasuClient = StarlasuClient(port = server!!.firstMappedPort, debug = true, repository = repoName
        )
        starlasuClient.lionWebClient.createRepository(repoName, LIONWEB_VERSION_USED_BY_STARLASU, false)
        starlasuClient.registerLanguage(todoLanguage)
        starlasuClient.registerLanguage(todoAccountLanguage)
        registerSerializersAndDeserializersInMetamodelRegistry()
        DefaultMetamodelRegistry.prepareSerialization(starlasuClient.jsonSerialization)

        // We create an empty partition
        val todoAccount = TodoAccount("my-wonderful-partition")
        val todoAccountId = starlasuClient.createPartition(todoAccount)

        val todoProject1 =
            TodoProject(
                "My errands list",
                mutableListOf(
                    Todo("Buy milk"),
                    Todo("garbage-out", "Take the garbage out"),
                    Todo("Go for a walk")
                )
            )
        todoProject1.assignParents()
        todoProject1.source = SyntheticSource("Project1")
        val todoProject1ID = starlasuClient.attachAST(todoProject1, todoAccount, containmentName = "projects")

        val todoProject2 =
            TodoProject(
                "My other errands list",
                mutableListOf(
                    Todo("BD", "Buy diary"),
                    Todo("WD", "Write in diary", ReferenceByName("BD")),
                    Todo("garbage-in", "Produce more garbage", ReferenceByName("garbage-out"))
                )
            )
        todoProject2.assignParents()
        todoProject2.source = SyntheticSource("Project2")
        val todoProject2ID = starlasuClient.attachAST(todoProject2, todoAccount, containmentName = "projects")

        val retrievedPartition = starlasuClient.getLionWebNode(todoAccountId)

        // When retrieving the entire partition, the source should be set correctly, producing the right node id
        assertEquals(todoProject1ID, retrievedPartition.getChildrenByContainmentName("projects")[0].id)
        assertEquals(todoProject2ID, retrievedPartition.getChildrenByContainmentName("projects")[1].id)
    }
}
