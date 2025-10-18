package com.strumenta.starlasu.javalib

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Node

data class Library(
    val books: List<Book>,
    val team: List<TeamMember> = emptyList(),
) : Node()

interface TeamMember : ASTNode

data class Book(
    val title: String,
    val numberOfPages: Int,
) : Node()

data class Person(
    val seniority: Int,
    val name: String,
) : Node(),
    TeamMember
