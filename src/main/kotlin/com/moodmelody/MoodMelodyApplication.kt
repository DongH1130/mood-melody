package com.moodmelody

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MoodMelodyApplication

fun main(args: Array<String>) {
	runApplication<MoodMelodyApplication>(*args)
}
