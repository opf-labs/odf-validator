package org.openpreservation.odf.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;

import org.openpreservation.messages.MessageFactory;
import org.openpreservation.messages.MessageLog;
import org.openpreservation.messages.MessageLogImpl;
import org.openpreservation.messages.Messages;
import org.openpreservation.odf.xml.XmlChecker;
import org.openpreservation.odf.xml.XmlParseResult;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

public class Validator {
    private static final String MIME_TEMPLATE = "application/vnd.oasis.opendocument.spreadsheet-template";
    private static final String MIME_DOCUMENT = "application/vnd.oasis.opendocument.spreadsheet";
    private static final String NAME_META_INF = "META-INF";
    private static final String NAME_MANIFEST = "manifest.xml";
    private static final String PATH_MANIFEST = NAME_META_INF + "/" + NAME_MANIFEST;
    private static final String NAME_CONTENT = "content.xml";
    private static final String TAG_DOC = "office:document";
    private static final String TAG_MANIFEST = "manifest:manifest";
    private static final MessageFactory FACTORY = Messages.getInstance();

    public final MessageLog messages = new MessageLogImpl();

    public Validator() {
        super();
    }

    public void validate(final Path toValidate)
            throws SAXNotRecognizedException, SAXNotSupportedException, ParserConfigurationException, IOException {
        if (isPackage(toValidate)) {
            validatePackage(toValidate);
        } else {
            XmlChecker checker = new XmlChecker();
            XmlParseResult result = checker.parse(toValidate);
            if (result.isWellFormed) {
                if (result.isRootName(TAG_DOC)) {
                    this.messages.add(FACTORY.getInfo("PKG-3", result.version));
                    if (MIMETYPES.isDocument(result.mimeType)) {
                        this.messages.add(FACTORY.getInfo("PKG-4", "Document", result.mimeType));
                    } else if (MIMETYPES.isTemplate(result.mimeType)) {
                        this.messages.add(FACTORY.getInfo("PKG-4", "Template", result.mimeType));
                    } else {
                        this.messages.add(FACTORY.getError("PKG-5", result.mimeType));
                    }
                }
                result = checker.validate(toValidate);
                this.messages.add(result.messages);
            } else {
                this.messages.add(result.messages);
                this.messages.add(FACTORY.getError("PKG-7", toValidate));
            }
        }
    }

    public boolean isFile(Path toCheck) {
        // Check that the supplied path is an existing, regular file
        if ((toCheck == null || !Files.exists(toCheck) || !Files.isRegularFile(toCheck))) {
            this.messages.add(FACTORY.getFatal("SYS-1", toCheck));
            return false;
        }
        return true;
    }

    private boolean isPackage(Path toCheck) {
        try (ZipFile zipFile = new ZipFile(toCheck.toFile())) {
            return true;
        } catch (ZipException e) {
            this.messages.add(FACTORY.getInfo("ZIP-1", toCheck));
        } catch (IOException e) {
            this.messages.add(FACTORY.getFatal("SYS-2", toCheck, e.getMessage()));
        }
        return false;
    }

    private boolean validatePackage(Path toValidate) {
        boolean isValid = true;
        boolean manifestFound = false;
        try (ZipFile zipFile = new ZipFile(toValidate.toFile())) {
            for (ZipEntry entry : zipFile.stream().toArray(ZipEntry[]::new)) {
                if ((entry.getMethod() != ZipEntry.STORED) && (entry.getMethod() != ZipEntry.DEFLATED)) {
                    this.messages.add(FACTORY.getError("ZIP-2", entry.getName()));
                    isValid = false;
                }
                if (entry.getName().startsWith(NAME_META_INF)) {
                    if (entry.isDirectory() && !NAME_META_INF.equals(entry.getName())) {
                        this.messages.add(FACTORY.getError("PKG-1", entry.getName()));
                    } else if (entry.getName().equals(PATH_MANIFEST)) {
                        manifestFound = true;
                    }
                }
            }
            if (!manifestFound) {
                this.messages.add(FACTORY.getError("PKG-2"));
                isValid = false;
            }
        } catch (IOException e) {
            this.messages.add(FACTORY.getError("ZIP-3", toValidate.toString(), e.getMessage()));
            isValid = false;
        }
        return isValid;
    }

    private boolean validateMetInfEntry(final ZipFile packageZip, final ZipEntry metaInfEntry) throws IOException {
        boolean isValid = true;
        if (metaInfEntry.getName().equals(PATH_MANIFEST)) {
            try {
                XmlChecker checker = new XmlChecker();
                XmlParseResult result = checker.parse(packageZip.getInputStream(metaInfEntry),
                        metaInfEntry.getName());
                if (result.isWellFormed) {
                    if (!result.isRootName(TAG_MANIFEST)) {
                        this.messages.add(FACTORY.getError("XML-2", metaInfEntry.getName()));
                        isValid = false;
                    }
                }
            } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
                this.messages.add(FACTORY.getError("SYS-4", PATH_MANIFEST, e.getMessage()));
            }
        } else if (metaInfEntry.getName().contains("signatures")) {
            // legal so parse as a digital signature
        }
        return isValid;
    }

    public enum MIMETYPES {
        TEMPLATE(MIME_TEMPLATE),
        DOCUMENT(MIME_DOCUMENT);

        public final String value;

        private MIMETYPES(final String value) {
            this.value = value;
        }

        public static boolean isTemplate(final String mime) {
            return mime.toLowerCase().equals(TEMPLATE.value);
        }

        public static boolean isDocument(final String mime) {
            return mime.toLowerCase().equals(DOCUMENT.value);
        }
    }
}