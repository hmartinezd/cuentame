package com.venkoi.restaurantops.core.model.menupackage

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*

sealed class MenuPackageDecodeResult { data class Success(val value:MenuPackageV1):MenuPackageDecodeResult();data class InvalidJson(val cause:Throwable):MenuPackageDecodeResult();data class InvalidPackage(val failure:MenuPackageValidationFailure):MenuPackageDecodeResult() }

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object MenuPackageJsonCodec {
    private val json=Json{ignoreUnknownKeys=false;coerceInputValues=false;explicitNulls=true;prettyPrint=true;prettyPrintIndent="  "}
    fun encode(value:MenuPackageV1):String {
        MenuPackageValidator.validate(value)?.let{throw IllegalArgumentException("Invalid MenuPackage: ${it.code}")}
        return json.encodeToString(value)
    }
    fun decodeAndValidate(text:String):MenuPackageDecodeResult { return try {
        val element=json.parseToJsonElement(text)
        if(!hasStrictTypes(element))return MenuPackageDecodeResult.InvalidJson(IllegalArgumentException("Invalid MenuPackage field type"))
        val value=json.decodeFromString<MenuPackageV1>(text)
        MenuPackageValidator.validate(value)?.let{MenuPackageDecodeResult.InvalidPackage(it)}?:MenuPackageDecodeResult.Success(value)
    }catch(e:SerializationException){MenuPackageDecodeResult.InvalidJson(e)}catch(e:IllegalArgumentException){MenuPackageDecodeResult.InvalidJson(e)} }

    private fun hasStrictTypes(element:JsonElement):Boolean=runCatching{
        val root=element.jsonObject
        root.string("format");root.number("formatVersion");root.string("packageId");root.string("restaurantId");root.string("publishedAt");root.string("currency")
        val menu=root.getValue("menu").jsonObject
        menu.string("menuId");menu.string("name");menu["description"]?.let{if(it !is JsonNull&&!it.jsonPrimitive.isString)error("description")};menu.number("publicationRevision");menu.string("defaultCashDiscountPercent")
        menu.getValue("categories").jsonArray.forEach{categoryElement->val category=categoryElement.jsonObject;category.string("categoryId");category.string("name");category.number("sortOrder");category.getValue("items").jsonArray.forEach{itemElement->val item=itemElement.jsonObject;item.string("sellableItemId");item.string("displayName");item.string("price");item.number("sortOrder");item.string("cashDiscountBehavior");item.number("commercialRevision");item.number("consumptionRevision")}}
    }.isSuccess
    private fun JsonObject.string(key:String){if(!getValue(key).jsonPrimitive.isString)error(key)}
    private fun JsonObject.number(key:String){if(getValue(key).jsonPrimitive.isString)error(key)}
}
