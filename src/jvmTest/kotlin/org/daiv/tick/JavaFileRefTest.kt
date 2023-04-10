package org.daiv.tick

import java.io.File
import kotlin.test.*

class JavaFileRefTest {

    val dir = File("JavaFileRefTest")
    val dir2 = File("${dir.name}/testDir/testDirNext")
    val file = File("${dir.name}/testFile")

    @BeforeTest
    fun beforeTest(){
        dir.mkdirs()

    }

    @AfterTest
    fun afterTest(){
        dir2.deleteRecursively()
        dir.deleteRecursively()
    }

    @Test
    fun testFile(){
        val ref = JavaFileRef(file)
        assertFalse(ref.exists())
        file.createNewFile()
        assertTrue(ref.exists())
        ref.delete()
        assertFalse(File(ref.fileName).exists())
    }

    @Test
    fun testDir(){
        val dirRef = JavaFileRef(dir2)
        assertFalse(dirRef.exists())
        dirRef.mkdirs()
        assertTrue(dirRef.exists())
        assertTrue(dirRef.listFiles().isEmpty())
        val newFile = File("${dir2.absolutePath}/child")
        newFile.createNewFile()
        val list = dirRef.listFiles()
        assertTrue(list.size == 1)
        assertEquals("child",list.first().fileName)
    }
}

