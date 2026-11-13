package hunoia.luno.runtime.floatball.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodeParserTest {

    @Test
    fun `parses a chinese verification code`() {
        assertEquals("123456", VerificationCodeParser.parse("【某应用】验证码 123456，5分钟内有效"))
    }

    @Test
    fun `parses a verification code from english text`() {
        assertEquals("8842", VerificationCodeParser.parse("Your verification code is 8842"))
    }

    @Test
    fun `reads the code from big text when body is empty`() {
        assertEquals("5566", VerificationCodeParser.parse("登录提醒", null, "验证码：5566"))
    }

    @Test
    fun `normalizes full width digits`() {
        assertEquals("1234", VerificationCodeParser.parse("验证码１２３４"))
    }

    @Test
    fun `rejects order numbers`() {
        assertNull(VerificationCodeParser.parse("订单号 12345678 已发货"))
    }

    @Test
    fun `rejects phone tail numbers`() {
        assertNull(VerificationCodeParser.parse("手机尾号 8888 的验证码已发送"))
    }

    @Test
    fun `nearest marker decides between keyword and exclusion`() {
        assertEquals(
            "1234",
            VerificationCodeParser.parse("验证码 1234，订单号 5678"),
        )
        assertNull(VerificationCodeParser.parse("订单号 5678，退款金额 1234"))
    }

    @Test
    fun `rejects when there are multiple candidate codes`() {
        assertNull(VerificationCodeParser.parse("验证码 1234，备用验证码 5678"))
    }

    @Test
    fun `accepts the same code appearing twice`() {
        assertEquals("4321", VerificationCodeParser.parse("验证码 4321，请勿泄露验证码 4321"))
    }

    @Test
    fun `ignores numbers that are part of longer digit runs`() {
        assertNull(VerificationCodeParser.parse("验证码 1234567890"))
    }

    @Test
    fun `rejects when no keyword exists`() {
        assertNull(VerificationCodeParser.parse("你的号码是 1234"))
    }

    @Test
    fun `does not treat otp inside another word as a keyword`() {
        assertNull(VerificationCodeParser.parse("hotpot 1234"))
    }

    @Test
    fun `rejects when the keyword is too far away`() {
        assertNull(
            VerificationCodeParser.parse(
                "验证码使用说明：这是一段很长的说明文字，和数字没有任何关系 1234",
            ),
        )
    }
}
