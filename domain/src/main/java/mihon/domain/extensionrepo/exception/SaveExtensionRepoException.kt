package mihon.domain.extensionrepo.exception

import java.io.IOException

class SaveExtensionRepoException(throwable: Throwable) : IOException("Error Saving Repository to Database", throwable)
