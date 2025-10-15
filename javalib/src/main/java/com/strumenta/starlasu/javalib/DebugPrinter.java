package com.strumenta.starlasu.javalib;

import com.strumenta.starlasu.model.ASTNode;
import com.strumenta.starlasu.model.DebugPrintConfiguration;
import com.strumenta.starlasu.model.BaseASTNode;
import com.strumenta.starlasu.model.PrintingKt;

/**
 * This class permits to print nodes, using a custom configuration
 */
public class DebugPrinter {

    private DebugPrintConfiguration configuration = new DebugPrintConfiguration();

    public DebugPrintConfiguration getConfiguration() {
        return this.configuration;
    }

    public String printNodeToString(ASTNode node) {
        return PrintingKt.debugPrint(node, "", configuration);
    }

    public void printNodeOnConsole(ASTNode node) {
        System.out.println(printNodeToString(node));
    }
}
