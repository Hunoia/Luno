package hunoia.luno.config.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动备份命名与保留策略回归。
 *
 * 这两条规则决定了「共享目录里到底删谁」——删错会毁掉用户自己放进同目录的文件，
 * 所以识别必须只认自己的命名、清理必须只在自己的文件里排序。
 */
class AutoBackupNamingTest {

    private fun name(day: Int): String = "luno-backup-202611%02d-030000.zip".format(day)

    @Test
    fun `generated display name is recognized as ours`() {
        val generated = AutoBackupRepository.newDisplayName(1_760_000_000_000L)
        assertTrue(generated, AutoBackupRepository.isAutoBackupName(generated))
        assertTrue(generated.startsWith(AutoBackupRepository.FILE_PREFIX))
        assertTrue(generated.endsWith(".zip"))
    }

    @Test
    fun `foreign or malformed names are not recognized`() {
        val rejected = listOf(
            "Luno_20261108_031500.zip",      // 手动备份的命名
            "luno-backup.zip",               // 缺时间戳
            "luno-backup-.zip",
            "luno-backup-20261108.zip",      // 只有日期
            "luno-backup-20261108-0315.zip", // 时间不足 6 位
            "luno-backup-20261108-031500-x.zip", // 序号不是数字
            "luno-backup-20261108-031500.zip.bak",
            "luno-backup-20261108-031500.txt",
            "notes.txt",
        )
        rejected.forEach { assertFalse(it, AutoBackupRepository.isAutoBackupName(it)) }
    }

    @Test
    fun `prune keeps newest 7 and drops only ours`() {
        val mine = (1..10).map { day -> name(day) }
        val foreign = listOf("我的备份.zip", "luno-backup-20261101-030000.zip.bak")

        // 10 份里最旧的 3 份被删，其余 7 份与外来文件都保留。
        assertEquals(
            listOf(name(3), name(2), name(1)),
            AutoBackupRepository.selectPrunable(mine + foreign),
        )
        foreign.forEach { assertFalse(it in AutoBackupRepository.selectPrunable(mine + foreign)) }
    }

    @Test
    fun `prune keeps everything while at or below the limit`() {
        val few = (4..10).map { day -> name(day) }
        assertEquals(emptyList<String>(), AutoBackupRepository.selectPrunable(few))
        assertEquals(emptyList<String>(), AutoBackupRepository.selectPrunable(emptyList()))
    }

    @Test
    fun `prune with keep zero drops every one of ours`() {
        val mine = (1..3).map { day -> name(day) }
        assertEquals(
            listOf(name(3), name(2), name(1)),
            AutoBackupRepository.selectPrunable(mine, keep = 0),
        )
    }

    @Test
    fun `collision-suffixed names are ours too and stay in the rotation`() {
        val base = name(8)
        val suffixed = base.replace(".zip", "-1.zip")
        assertTrue(AutoBackupRepository.isAutoBackupName(suffixed))

        // 8 个正常名 + 1 个撞车名 = 9 份，保留 7 → 只删最旧的 2 份；
        // 撞车名与它的母本同属最新一天，必须活下来（否则会无限堆积）。
        val all = (1..8).map { day -> name(day) } + suffixed
        assertEquals(listOf(name(2), name(1)), AutoBackupRepository.selectPrunable(all))
        assertFalse(suffixed in AutoBackupRepository.selectPrunable(all))
    }

    @Test
    fun `unique name never overwrites an existing file`() {
        val base = AutoBackupRepository.newDisplayName(1_760_000_000_000L)
        val first = "$base".replace(".zip", "-1.zip")
        val second = "$base".replace(".zip", "-2.zip")

        assertEquals(base, AutoBackupRepository.uniqueName(base, emptyList()))
        assertEquals(first, AutoBackupRepository.uniqueName(base, listOf(base)))
        assertEquals(second, AutoBackupRepository.uniqueName(base, listOf(base, first)))
        // 三个名字互不相同且都还是合法的自动备份名。
        listOf(base, first, second).forEach {
            assertTrue(it, AutoBackupRepository.isAutoBackupName(it))
        }
    }
}
