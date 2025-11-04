package com.guillem.llistacompra // Substitueix pel teu package

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

// --- 1. MODELS DE DADES (SIMULACIÓ) ---

// Defineix la Categoria
data class Categoria(
    val id: String = UUID.randomUUID().toString(),
    val nom: String
)

// Defineix el Producte
data class Producte(
    val id: String = UUID.randomUUID().toString(),
    val idCategoria: String, // Clau forana a Categoria.id
    var nom: String,
    var comprat: Boolean = false // Requisit: Marcar com a completat
)

class MainActivity : AppCompatActivity() {

    // --- Variables de la UI ---
    private lateinit var spinnerCategories: Spinner
    private lateinit var recyclerViewProductes: RecyclerView
    private lateinit var producteAdapter: ProducteAdapter

    // --- Variables de Dades (SIMULADES) ---
    private val totesCategories = listOf(
        Categoria(id = "c1", nom = "Fruita i Verdura"),
        Categoria(id = "c2", nom = "Làctics"),
        Categoria(id = "c3", nom = "Neteja")
    )

    private val totsProductes = mutableListOf(
        Producte(idCategoria = "c1", nom = "Pomes Fuji", comprat = false),
        Producte(idCategoria = "c1", nom = "Tomàquets Pera", comprat = true),
        Producte(idCategoria = "c2", nom = "Llet Semi", comprat = false),
        Producte(idCategoria = "c2", nom = "Iogurts Naturals", comprat = false),
        Producte(idCategoria = "c3", nom = "Detergent Roba", comprat = true),
        Producte(idCategoria = "c3", nom = "Esponja", comprat = false)
    )

    private var categoriaSeleccionadaId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicialització de Vistes
        spinnerCategories = findViewById(R.id.spinner_categories)
        recyclerViewProductes = findViewById(R.id.recycler_view_productes)

        // 2. CONFIGURACIÓ DEL RECYCLERVIEW
        producteAdapter = ProducteAdapter(
            totsProductes.filter { false }.toMutableList(), // Llista inicialment buida
            // Lambdas per gestionar les accions del producte (CRUD/Estat)
            onCheckboxClicked = { producte -> actualitzarEstatProducte(producte) },
            onDeleteClicked = { producte -> eliminarProducte(producte) }
        )
        recyclerViewProductes.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = producteAdapter
        }

        // 3. CONFIGURACIÓ DE L'SPINNER
        setupSpinner()
    }

    // --- MÈTODE PER CONFIGURAR L'SPINNER ---
    private fun setupSpinner() {
        val nomsCategories = totesCategories.map { it.nom }
        val adapterSpinner = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            nomsCategories
        )
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategories.adapter = adapterSpinner

        // Listener per quan es selecciona una categoria
        spinnerCategories.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Obtenim l'ID de la categoria seleccionada
                categoriaSeleccionadaId = totesCategories[position].id
                // Filtrem i actualitzem el RecyclerView
                carregarProductesPerCategoria(categoriaSeleccionadaId!!)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // No cal fer res si no es selecciona res
            }
        }
    }

    // --- MÈTODE PER ACTUALITZAR EL RECYCLERVIEW ---
    private fun carregarProductesPerCategoria(categoryId: String) {
        // En un projecte amb Room, aquí cridaríeu al DAO:
        // val productesFiltrats = producteDao.getProductsByCategory(categoryId)

        val productesFiltrats = totsProductes.filter { it.idCategoria == categoryId }.toMutableList()
        producteAdapter.updateList(productesFiltrats)
    }

    // --- MÈTODES DE LÒGICA DE LA LLISTA (CRUD I ESTAT) ---

    private fun actualitzarEstatProducte(producte: Producte) {
        // 1. Trobar l'objecte original a la llista (simulant l'actualització a la BD)
        val index = totsProductes.indexOfFirst { it.id == producte.id }
        if (index != -1) {
            // 2. Invertir l'estat (de comprat a pendent o viceversa)
            totsProductes[index].comprat = producte.comprat
            Toast.makeText(this, "Estat de '${producte.nom}' actualitzat.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun eliminarProducte(producte: Producte) {
        // 1. Eliminar de la llista mestra (simulant l'eliminació a la BD)
        totsProductes.remove(producte)
        // 2. Actualitzar la vista amb la categoria actual
        categoriaSeleccionadaId?.let { carregarProductesPerCategoria(it) }
        Toast.makeText(this, "'${producte.nom}' eliminat.", Toast.LENGTH_SHORT).show()
    }

    // Mètode per afegir productes (no implementat en detall aquí, es llança des del FAB)
    // El FAB per afegir producte (fab_add_producte) hauria de:
    // 1. Obrir un Diàleg.
    // 2. Demanar el Nom.
    // 3. Afegir el nou Producte a 'totsProductes' amb la 'categoriaSeleccionadaId'.
    // 4. Cridar 'carregarProductesPerCategoria(categoriaSeleccionadaId!!)'.
}

// --- 4. ADAPTADOR DEL RECYCLERVIEW ---

class ProducteAdapter(
    private var productes: MutableList<Producte>,
    // Listeners per gestionar les accions de la UI
    private val onCheckboxClicked: (Producte) -> Unit,
    private val onDeleteClicked: (Producte) -> Unit
) : RecyclerView.Adapter<ProducteAdapter.ProducteViewHolder>() {

    // El ViewHolder conté les referències a les vistes de item_producte.xml
    inner class ProducteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkboxComprat: CheckBox = itemView.findViewById(R.id.checkbox_comprat)
        val textNomProducte: TextView = itemView.findViewById(R.id.text_nom_producte)
        val buttonDelete: ImageButton = itemView.findViewById(R.id.button_delete)

        fun bind(producte: Producte) {
            textNomProducte.text = producte.nom
            checkboxComprat.isChecked = producte.comprat

            // LÒGICA DE MARCATGE (Requisit: no s'elimina)
            checkboxComprat.setOnCheckedChangeListener(null) // Evitar crides dobles
            checkboxComprat.isChecked = producte.comprat
            checkboxComprat.setOnCheckedChangeListener { _, isChecked ->
                producte.comprat = isChecked // Actualitza l'estat local del model
                onCheckboxClicked(producte)  // Crida al mètode d'actualització de l'Activity
            }

            // LÒGICA D'ELIMINACIÓ (CRUD: Delete)
            buttonDelete.setOnClickListener {
                onDeleteClicked(producte)
            }

            // LÒGICA D'EDICIÓ (CRUD: Edit) - Podria anar en un clic llarg o en el mateix ítem
            itemView.setOnClickListener {
                // Aquí podríeu obrir un diàleg per editar el nom del producte
            }

            // Opcional: Estils per productes completats
            if (producte.comprat) {
                // P. ex., text ratllat
                // textNomProducte.paintFlags = textNomProducte.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                // textNomProducte.paintFlags = textNomProducte.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProducteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producte, parent, false)
        return ProducteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProducteViewHolder, position: Int) {
        holder.bind(productes[position])
    }

    override fun getItemCount(): Int = productes.size

    // Mètode per actualitzar les dades del RecyclerView quan canvia la categoria
    fun updateList(newList: MutableList<Producte>) {
        productes = newList
        notifyDataSetChanged()
    }
}