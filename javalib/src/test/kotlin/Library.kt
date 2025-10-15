package com.strumenta.starlasu.javalib

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.NodeType

data class Library(
    val books: List<Book>,
    val team: List<TeamMember> = emptyList(),
) : Node()

@NodeType
interface TeamMember

data class Book(
    val title: String,
    val numberOfPages: Int,
) : Node()

data class Person(
    val seniority: Int,
    val name: String,
) : Node(),
    TeamMember
