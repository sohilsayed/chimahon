package tachiyomi.domain.custombuttons.exception

import java.io.IOException

class SaveCustomButtonException(throwable: Throwable) : IOException("Error Saving Custom Button to Database", throwable)
