package it

import _root_.play.api.http.SecretConfiguration
import _root_.play.api.libs.crypto.DefaultCookieSigner
import _root_.play.api.mvc.SessionCookieBaker
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.SessionCookieCrypto

trait SessionCookieCryptoFilterWrapper {
  val sc = SecretConfiguration("secret")
  val cs = new DefaultCookieSigner(sc)
  val mtdpSessionCookie="mdtp"
  val signSeparator="-"

  val cookieBaker: SessionCookieBaker
  val sessionCookieCrypto: SessionCookieCrypto

  def encryptCookie(sessionData: Map[String, String]): String = {
    val encoded = cookieBaker.encode(sessionData)
    val encrypted: String = sessionCookieCrypto.crypto.encrypt(PlainText(encoded)).value
    s"""mdtp=$encrypted"""
  }
}
