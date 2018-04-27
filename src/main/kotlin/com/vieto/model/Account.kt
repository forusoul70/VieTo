package com.vieto.model

import org.springframework.data.annotation.Id

class Account(@Id val id:String, val account: Account, val name: String) {
}