import datadog.trace.instrumentation.jdbc.JDBCUtils
import datadog.trace.util.test.DDSpecification

class SqlNormalizerTest extends DDSpecification {

  def "normalize #originalSql"() {
    setup:
    def actualNormalized = JDBCUtils.normalizeSql(originalSql)

    expect:
    actualNormalized == normalizedSql

    where:
    originalSql                                                                | normalizedSql
    // Numbers
    "SELECT * FROM TABLE WHERE FIELD=1234"                                     | "SELECT * FROM TABLE WHERE FIELD=?"
    "SELECT * FROM TABLE WHERE FIELD = 1234"                                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD>=-1234"                                   | "SELECT * FROM TABLE WHERE FIELD>=?"
    "SELECT * FROM TABLE WHERE FIELD<-1234"                                    | "SELECT * FROM TABLE WHERE FIELD<?"
    "SELECT * FROM TABLE WHERE FIELD <.1234"                                   | "SELECT * FROM TABLE WHERE FIELD <?"
    "SELECT 1.2"                                                               | "SELECT ?"
    "SELECT -1.2"                                                              | "SELECT ?"
    "SELECT -1.2e-9"                                                           | "SELECT ?"
    "SELECT 2E+9"                                                              | "SELECT ?"
    "SELECT +0.2"                                                              | "SELECT ?"
    "SELECT .2"                                                                | "SELECT ?"
    "7"                                                                        | "?"
    ".7"                                                                       | "?"
    "-7"                                                                       | "?"
    "+7"                                                                       | "?"
    "SELECT 0x0af764"                                                          | "SELECT ?"
    "SELECT 0xdeadbeef"                                                        | "SELECT ?"

    // Not numbers but could be confused as such
    "SELECT A + B"                                                             | "SELECT A + B"
    "SELECT -- comment"                                                        | "SELECT -- comment"
    "SELECT * FROM TABLE123"                                                   | "SELECT * FROM TABLE123"
    "SELECT FIELD2 FROM TABLE_123 WHERE X<>7"                                  | "SELECT FIELD2 FROM TABLE_123 WHERE X<>?"

    // Semi-nonsensical almost-numbers to elide or not
    "SELECT --83--...--8e+76e3E-1"                                             | "SELECT ?"
    "SELECT DEADBEEF"                                                          | "SELECT DEADBEEF"
    "SELECT 123-45-6789"                                                       | "SELECT ?"
    "SELECT 1/2/34"                                                            | "SELECT ?/?/?"

    // Basic ' strings
    "SELECT * FROM TABLE WHERE FIELD = ''"                                     | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = 'words and spaces'"                     | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = ' an escaped '' quote mark inside'"     | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\\\\'"                                 | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\"inside doubles\"'"                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\"\"'"                                 | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = 'a single \" doublequote inside'"       | "SELECT * FROM TABLE WHERE FIELD = ?"

    // Some databases support/encourage " instead of ' with same escape rules
    "SELECT * FROM TABLE WHERE FIELD = \"\""                                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"words and spaces'\""                  | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \" an escaped \"\" quote mark inside\"" | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"\\\\\""                               | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"'inside singles'\""                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"''\""                                 | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"a single ' singlequote inside\""      | "SELECT * FROM TABLE WHERE FIELD = ?"


  }
}
