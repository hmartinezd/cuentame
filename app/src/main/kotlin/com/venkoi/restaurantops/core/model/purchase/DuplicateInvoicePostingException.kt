package com.venkoi.restaurantops.core.model.purchase

class DuplicateInvoicePostingException(val candidate: DuplicateInvoiceCandidate) :
    IllegalStateException("Strong duplicate invoice requires explicit override")
