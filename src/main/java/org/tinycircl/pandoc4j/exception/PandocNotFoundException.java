package org.tinycircl.pandoc4j.exception;

public class PandocNotFoundException extends PandocException {

    public PandocNotFoundException() {
        super("Pandoc executable not found. Please install Pandoc (https://pandoc.org/installing.html) " +
              "or set the PANDOC_PATH environment variable / pandoc.path system property.");
    }

    public PandocNotFoundException(String searchedPath) {
        super("Pandoc executable not found at: " + searchedPath +
              ". Please install Pandoc (https://pandoc.org/installing.html) " +
              "or set the PANDOC_PATH environment variable / pandoc.path system property.");
    }
}
