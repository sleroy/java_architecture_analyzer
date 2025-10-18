#!/bin/bash
echo "Compiling EJB inspectors to verify architectural compliance fixes..."
mvn compile -q
echo "Compilation result: $?"
