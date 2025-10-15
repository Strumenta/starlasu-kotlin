package com.strumenta.starlasu.javalib;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;

public class DebugPrinterTest {

    @Test
    public void testSimpleCase() {
        Library root = new Library(Arrays.asList(
                new Book("Effective Kolasu", 50),
                new Book("Extraordinaty Pylasu", 40),
                new Book("Magic Tylasu", 140)),
                new LinkedList<>()
        );
        DebugPrinter debugPrinter = new DebugPrinter();
        assertEquals("Library {\n" +
                "  books = [\n" +
                "    Book {\n" +
                "      numberOfPages = 50\n" +
                "      title = Effective Kolasu\n" +
                "    } // Book\n" +
                "    Book {\n" +
                "      numberOfPages = 40\n" +
                "      title = Extraordinaty Pylasu\n" +
                "    } // Book\n" +
                "    Book {\n" +
                "      numberOfPages = 140\n" +
                "      title = Magic Tylasu\n" +
                "    } // Book\n" +
                "  ]\n" +
                "  team = []\n" +
                "} // Library\n", debugPrinter.printNodeToString(root));
    }

    @Test
    public void testCaseInvolvingNodeType() {
        Library root = new Library(
                new LinkedList<>(),
                Arrays.asList(
                        new Person(1, "Jack Junior"),
                        new Person(28, "Tim Leader"))
        );
        DebugPrinter debugPrinter = new DebugPrinter();
        assertEquals("Library {\n" +
                "  books = []\n" +
                "  team = [\n" +
                "    Person {\n" +
                "      name = Jack Junior\n" +
                "      seniority = 1\n" +
                "    } // Person\n" +
                "    Person {\n" +
                "      name = Tim Leader\n" +
                "      seniority = 28\n" +
                "    } // Person\n" +
                "  ]\n" +
                "} // Library\n", debugPrinter.printNodeToString(root));
    }
}
