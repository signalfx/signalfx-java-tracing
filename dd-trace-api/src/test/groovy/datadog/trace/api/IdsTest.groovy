// Modified by SignalFx
package datadog.trace.api

import datadog.trace.util.test.DDSpecification

class IdsTest extends DDSpecification {

  def "idToHex"() {
    when:
    def toHex = Ids.idToHex(toConvert)

    then:
    toHex == expected

    where:
    toConvert                                 | expected
    "0"                                       | "0000000000000000"
    "1"                                       | "0000000000000001"
    "4294967295"                              | "00000000ffffffff"
    "18446744073709551615"                    | "ffffffffffffffff"
    "18446744073709551616"                    | "00000000000000010000000000000000"
    "340282366920938463463374607431768211455" | "ffffffffffffffffffffffffffffffff"
  }

  def "hexToId"() {
    when:
    def toDec = Ids.hexToId(toConvert)

    then:
    toDec == expected

    where:
    toConvert                          | expected
    "0000000000000000"                 | "0"
    "0000000000000001"                 | "1"
    "00000000ffffffff"                 | "4294967295"
    "ffffffffffffffff"                 | "18446744073709551615"
    "00000000000000010000000000000000" | "18446744073709551616"
    "ffffffffffffffffffffffffffffffff" | "340282366920938463463374607431768211455"
  }
}
