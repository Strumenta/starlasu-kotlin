package com.strumenta.starlasu.serialization

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.IssueSeverity
import com.strumenta.starlasu.validation.Result
import org.junit.Test
import kotlin.test.assertEquals

class XmlGenerationTest {
    @Test
    fun generateXMLBasic() {
        val myRoot =
            MyRoot(
                mainSection =
                    Section(
                        "Section1",
                        emptyList(),
                    ),
                otherSections = listOf(),
            )
        val xml = XMLGenerator().generateString(myRoot)
        assertEquals(
            """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<root type="MyRoot">
    <mainSection name="Section1" type="Section"/>
</root>
""".replace("\n", System.lineSeparator()),
            xml,
        )
    }

    @Test
    fun generateXML() {
        val myRoot =
            MyRoot(
                mainSection =
                    Section(
                        "Section1",
                        listOf(
                            Content(1, null),
                            Content(2, Content(3, Content(4, null))),
                        ),
                    ),
                otherSections = listOf(),
            )
        val xml = XMLGenerator().generateString(myRoot)
        assertEquals(
            """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<root type="MyRoot">
    <mainSection name="Section1" type="Section">
        <contents _id="1" type="Content">
            <annidatedContent/>
        </contents>
        <contents _id="2" type="Content">
            <annidatedContent _id="3" type="Content">
                <annidatedContent _id="4" type="Content">
                    <annidatedContent/>
                </annidatedContent>
            </annidatedContent>
        </contents>
    </mainSection>
</root>
""".replace("\n", System.lineSeparator()),
            xml,
        )
    }

    @Test
    fun generateXMLWithListOfValues() {
        val myRoot =
            MyRoot(
                mainSection =
                    Section(
                        "Section1",
                        listOf(
                            Content(1, null),
                            OtherContent(listOf(1, 2, 3, 100, -122)),
                            Content(2, Content(3, Content(4, null))),
                        ),
                    ),
                otherSections = listOf(),
            )
        val xml = XMLGenerator().generateString(myRoot)
        assertEquals(
            """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<root type="MyRoot">
    <mainSection name="Section1" type="Section">
        <contents _id="1" type="Content">
            <annidatedContent/>
        </contents>
        <contents type="OtherContent">
            <values value="1"/>
            <values value="2"/>
            <values value="3"/>
            <values value="100"/>
            <values value="-122"/>
        </contents>
        <contents _id="2" type="Content">
            <annidatedContent _id="3" type="Content">
                <annidatedContent _id="4" type="Content">
                    <annidatedContent/>
                </annidatedContent>
            </annidatedContent>
        </contents>
    </mainSection>
</root>
""".replace("\n", System.lineSeparator()),
            xml,
        )
    }

    @Test
    fun generateXMLWithIssues() {
        val issues: List<Issue> =
            listOf(
                Issue.lexical("lexical problem"),
                Issue.semantic(
                    "semantic problem",
                    severity = IssueSeverity.ERROR,
                    position = Position(Point(10, 1), Point(12, 3)),
                ),
                Issue.semantic(
                    "semantic warning",
                    severity = IssueSeverity.WARNING,
                    position = Position(Point(10, 1), Point(12, 3)),
                ),
                Issue.semantic(
                    "semantic info",
                    severity = IssueSeverity.INFO,
                    position = Position(Point(10, 1), Point(12, 3)),
                ),
            )
        val result = Result<Node>(issues, null)
        val serialized = XMLGenerator().generateString(result)
        assertEquals(
            """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<result>
    <issues>
        <Issue message="lexical problem" severity="ERROR" type="LEXICAL"/>
        <Issue message="semantic problem" severity="ERROR" type="SEMANTIC">
            <position description="Position(start=Line 10, Column 1, end=Line 12, Column 3)">
                <start column="1" line="10"/>
                <end column="3" line="12"/>
            </position>
        </Issue>
        <Issue message="semantic warning" severity="WARNING" type="SEMANTIC">
            <position description="Position(start=Line 10, Column 1, end=Line 12, Column 3)">
                <start column="1" line="10"/>
                <end column="3" line="12"/>
            </position>
        </Issue>
        <Issue message="semantic info" severity="INFO" type="SEMANTIC">
            <position description="Position(start=Line 10, Column 1, end=Line 12, Column 3)">
                <start column="1" line="10"/>
                <end column="3" line="12"/>
            </position>
        </Issue>
    </issues>
    <root/>
</result>""".replace("\n", System.lineSeparator()),
            serialized.trim(),
        )
    }
}
