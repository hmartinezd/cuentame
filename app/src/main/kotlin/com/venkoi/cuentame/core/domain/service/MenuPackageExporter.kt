package com.venkoi.cuentame.core.domain.service

import com.venkoi.cuentame.core.common.ids.MenuPublicationId
import com.venkoi.cuentame.core.domain.repository.MenuPublicationRepository
import com.venkoi.cuentame.core.model.menupackage.*
import javax.inject.Inject
import javax.inject.Singleton

data class MenuPackageExport(val suggestedFileName:String,val mimeType:String="application/json",val bytes:ByteArray)
sealed class MenuPackageExportException(message:String):Exception(message){class PublicationNotFound:MenuPackageExportException("Publication not found");class MalformedPublication(cause:Throwable):MenuPackageExportException("Publication is malformed"){init{initCause(cause)}};class ValidationFailed(val failure:MenuPackageValidationFailure):MenuPackageExportException("MenuPackage validation failed")}

@Singleton
class MenuPackageExporter @Inject constructor(private val publications:MenuPublicationRepository){
    suspend fun prepare(publicationId:MenuPublicationId):MenuPackageExport{
        val snapshot=publications.getPublication(publicationId)?:throw MenuPackageExportException.PublicationNotFound()
        val value=try{MenuPackageFactory.create(snapshot)}catch(e:IllegalArgumentException){throw MenuPackageExportException.MalformedPublication(e)}
        MenuPackageValidator.validate(value)?.let{throw MenuPackageExportException.ValidationFailed(it)}
        return MenuPackageExport(suggestedName(value.menu.name,value.menu.publicationRevision),bytes=MenuPackageJsonCodec.encode(value).toByteArray(Charsets.UTF_8))
    }
    internal fun suggestedName(menuName:String,revision:Long):String{
        val slug=menuName.lowercase().replace(Regex("[^a-z0-9]+"),"-").trim('-').ifBlank{"menu"}.take(80).trimEnd('-')
        return "$slug-r$revision.cuentame-menu.json"
    }
}
