    package com.guillem.llistacompra

    import android.os.Bundle
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.AdapterView
    import android.widget.ArrayAdapter
    import android.widget.CheckBox
    import android.widget.EditText
    import android.widget.FrameLayout
    import android.widget.ImageButton
    import android.widget.Spinner
    import android.widget.TextView
    import android.widget.Toast
    import androidx.appcompat.app.AlertDialog
    import androidx.appcompat.app.AppCompatActivity
    import androidx.recyclerview.widget.LinearLayoutManager
    import androidx.recyclerview.widget.RecyclerView
    import com.google.android.material.floatingactionbutton.FloatingActionButton
    import java.util.UUID
    import com.google.firebase.firestore.ktx.firestore
    import com.google.firebase.ktx.Firebase

    // --- 1. MODELS DE DADES (SIMULACIÓ) ---
    data class Categoria(
        val id: String = UUID.randomUUID().toString(),
        var nom: String // El fem 'var' per poder editar-lo
    )

    data class Producte(
        val id: String = UUID.randomUUID().toString(),
        val idCategoria: String,
        var nom: String,
        var comprat: Boolean = false
    )

    class MainActivity : AppCompatActivity() {

        // --- Variables de la UI ---
        private lateinit var spinnerCategories: Spinner
        private lateinit var recyclerViewProductes: RecyclerView
        private lateinit var producteAdapter: ProducteAdapter
        // 👈 NOU: Botó per gestionar categories (Assumeix ID 'button_manage_categories' a activity_main.xml)
        private lateinit var btnManageCategories: ImageButton
        private val db = Firebase.firestore


        // --- Variables de Dades (MUTABLES PER PODER GESTIONAR-LES) ---
        private val totesCategories = mutableListOf<Categoria>()
        private val totsProductes = mutableListOf<Producte>()

        private var categoriaSeleccionadaId: String? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // Inicialització de Vistes
            spinnerCategories = findViewById(R.id.spinner_categories)
            recyclerViewProductes = findViewById(R.id.recycler_view_productes)
            // 👈 NOU: Inicialització del botó de gestió de categories
            // Assegura't que el teu activity_main.xml té aquest ID
            btnManageCategories = findViewById(R.id.button_manage_categories)

            // 2. CONFIGURACIÓ DEL RECYCLERVIEW
            producteAdapter = ProducteAdapter(
                totsProductes.filter { false }.toMutableList(),
                onCheckboxClicked = { producte -> actualitzarEstatProducte(producte) },
                onDeleteClicked = { producte -> eliminarProducte(producte) },
                onEditClicked = { producte -> mostrarDialogEditarProducte(producte) }
            )
            recyclerViewProductes.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = producteAdapter
            }

            // 3. CONFIGURACIÓ DE L'SPINNER
            setupSpinner()

            // 4. CONFIGURACIÓ DELS BOTONS D'ACCIÓ
            findViewById<FloatingActionButton>(R.id.fab_add_producte).setOnClickListener {
                mostrarDialogAfegirProducte()
            }
            // 👈 NOU: Listener per gestionar categories
            btnManageCategories.setOnClickListener {
                mostrarDialogGestioCategories()
            }

            carregarDadesDeFirestore()
        }

        // --- MÈTODE PER CONFIGURAR L'SPINNER ---
        private fun setupSpinner(selectFirst: Boolean = true) {
            if (totesCategories.isEmpty()) {
                Toast.makeText(this, "Crea una categoria primer.", Toast.LENGTH_LONG).show()
                categoriaSeleccionadaId = null
                producteAdapter.updateList(mutableListOf())
                // Carreguem un adapter buit si no hi ha categories
                val emptyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Sense categories"))
                spinnerCategories.adapter = emptyAdapter
                return
            }

            val nomsCategories = totesCategories.map { it.nom }
            val adapterSpinner = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                nomsCategories
            )
            adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategories.adapter = adapterSpinner

            if (selectFirst) {
                // Seleccionem la primera per defecte i carreguem productes
                categoriaSeleccionadaId = totesCategories.first().id
                carregarProductesPerCategoria(categoriaSeleccionadaId!!)
            }

            spinnerCategories.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    categoriaSeleccionadaId = totesCategories[position].id
                    carregarProductesPerCategoria(categoriaSeleccionadaId!!)
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }

        // --- MÈTODE PER ACTUALITZAR EL RECYCLERVIEW ---
        private fun carregarProductesPerCategoria(categoryId: String) {
            val productesFiltrats = totsProductes.filter { it.idCategoria == categoryId }.toMutableList()
            producteAdapter.updateList(productesFiltrats)
        }

        // --- MÈTODES DE LÒGICA DE PRODUCTES (CRUD) ---

        private fun actualitzarEstatProducte(producte: Producte) {
            val index = totsProductes.indexOfFirst { it.id == producte.id }
            if (index != -1) {
                totsProductes[index].comprat = producte.comprat

                // Actualiza Firestore
                db.collection("productes").document(producte.id)
                    .update("comprat", producte.comprat)

                // Refresca la lista filtrada por categoría
                categoriaSeleccionadaId?.let { carregarProductesPerCategoria(it) }
            }
        }

        private fun eliminarProducte(producte: Producte) {
            totsProductes.remove(producte)
            categoriaSeleccionadaId?.let { carregarProductesPerCategoria(it) }
            Toast.makeText(this, "'${producte.nom}' eliminat.", Toast.LENGTH_SHORT).show()

            // 🔥 Eliminar de Firestore
            db.collection("productes").document(producte.id).delete()
        }


        // ... (mostrarDialogEditarProducte i editarNomProducte es mantenen igual)

        private fun mostrarDialogEditarProducte(producte: Producte) {
            val input = EditText(this)
            input.setText(producte.nom)
            input.setSelection(producte.nom.length)

            val container = FrameLayout(this)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val density = resources.displayMetrics.density
            val margin = (16 * density).toInt()
            params.leftMargin = margin
            params.rightMargin = margin
            input.layoutParams = params
            container.addView(input)

            AlertDialog.Builder(this)
                .setTitle("Editar producte")
                .setMessage("Modifica el nom de '${producte.nom}':")
                .setView(container)
                .setPositiveButton("Guardar") { dialog, _ ->
                    val nouNom = input.text.toString()
                    editarNomProducte(producte, nouNom)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel·lar") { dialog, _ ->
                    dialog.cancel()
                }
                .show()
        }

        private fun editarNomProducte(producte: Producte, nouNom: String) {
            val nomTrim = nouNom.trim()

            if (nomTrim.isBlank()) {
                Toast.makeText(this, "El nom no pot ser buit.", Toast.LENGTH_SHORT).show()
                return
            }

            if (nomTrim == producte.nom) return

            val index = totsProductes.indexOfFirst { it.id == producte.id }
            if (index != -1) {
                totsProductes[index].nom = nomTrim
                categoriaSeleccionadaId?.let { carregarProductesPerCategoria(it) }
                Toast.makeText(this, "Producte editat a '$nomTrim'.", Toast.LENGTH_SHORT).show()
            }
        }

        private fun mostrarDialogAfegirProducte() {
            if (categoriaSeleccionadaId == null) {
                Toast.makeText(this, "Si us plau, crea i selecciona una categoria primer.", Toast.LENGTH_SHORT).show()
                return
            }

            val input = EditText(this)
            input.hint = "Nom del producte"

            val container = FrameLayout(this)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val density = resources.displayMetrics.density
            val margin = (16 * density).toInt()
            params.leftMargin = margin
            params.rightMargin = margin
            input.layoutParams = params
            container.addView(input)

            AlertDialog.Builder(this)
                .setTitle("Afegir nou producte")
                .setMessage("Introdueix el nom a la categoria demanada:")
                .setView(container)
                .setPositiveButton("Afegir") { dialog, _ ->
                    val nomProducte = input.text.toString()
                    afegirNouProducte(nomProducte)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel·lar") { dialog, _ ->
                    dialog.cancel()
                }
                .show()
        }

        // --- GESTIÓ DE CATEGORIES (CRUD) 👈 NOU BLOC ---

        private fun mostrarDialogGestioCategories() {
            // Crear una llista d'accions (Editar/Eliminar) per a cada categoria + Afegir
            val categoriesWithAction = totesCategories.map { it.nom } + listOf("➕ Afegir nova categoria")

            AlertDialog.Builder(this)
                .setTitle("Gestionar Categories")
                .setItems(categoriesWithAction.toTypedArray()) { dialog, which ->
                    if (which == categoriesWithAction.size - 1) {
                        // Últim element: Afegir nova categoria
                        mostrarDialogAfegirCategoria()
                    } else {
                        // Element existent: Editar o Eliminar
                        val categoriaAEditar = totesCategories[which]
                        mostrarDialogEdicioCategoria(categoriaAEditar)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Tancar", null)
                .show()
        }

        private fun mostrarDialogAfegirCategoria() {
            val input = EditText(this)
            input.hint = "Nom de la categoria"

            // Configuració de marges
            val container = FrameLayout(this)
            val density = resources.displayMetrics.density
            val margin = (16 * density).toInt()
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.leftMargin = margin
            params.rightMargin = margin
            input.layoutParams = params
            container.addView(input)

            AlertDialog.Builder(this)
                .setTitle("Nova Categoria")
                .setView(container)
                .setPositiveButton("Crear") { dialog, _ ->
                    afegirNovaCategoria(input.text.toString())
                }
                .setNegativeButton("Cancel·lar", null)
                .show()
        }

        private fun afegirNovaCategoria(nomCategoria: String) {
            val nomNet = nomCategoria.trim()
            if (nomNet.isBlank()) {
                Toast.makeText(this, "El nom de la categoria no pot ser buit.", Toast.LENGTH_SHORT).show()
                return
            }

            val novaCategoria = Categoria(nom = nomNet)
            totesCategories.add(novaCategoria)

            // 🔹 Guardar en Firestore
            db.collection("categories").document(novaCategoria.id).set(novaCategoria)

            setupSpinner(selectFirst = false) // Refresca el spinner

            // Si es la primera categoría, seleccionarla
            if (totesCategories.size == 1) {
                categoriaSeleccionadaId = novaCategoria.id
                carregarProductesPerCategoria(novaCategoria.id)
            }

            Toast.makeText(this, "Categoria '${novaCategoria.nom}' creada.", Toast.LENGTH_SHORT).show()
        }

        private fun afegirNouProducte(nomProducte: String) {
            val nomNet = nomProducte.trim()

            // Comprova que el nom no estigui buit després de treure espais
            if (nomNet.isBlank()) {
                Toast.makeText(this, "El nom no pot ser buit.", Toast.LENGTH_SHORT).show()
                return
            }

            val catId = categoriaSeleccionadaId

            if (catId != null) {
                val nouProducte = Producte(
                    idCategoria = catId,
                    nom = nomNet,
                    comprat = false
                )
                totsProductes.add(nouProducte) // Afegeix a la llista local
                carregarProductesPerCategoria(catId) // Refresca la UI

                // 🔥 Afegeix el producte a Firestore
                db.collection("productes").document(nouProducte.id).set(nouProducte)

                Toast.makeText(this, "'$nomNet' afegit correctament.", Toast.LENGTH_SHORT).show()
            }
        }

        private fun carregarDadesDeFirestore() {
            // 🔹 Categories
            db.collection("categories")
                .get()
                .addOnSuccessListener { result ->
                    totesCategories.clear()
                    for (doc in result) {
                        val id = doc.getString("id") ?: continue
                        val nom = doc.getString("nom") ?: ""
                        totesCategories.add(Categoria(id, nom))
                    }
                    setupSpinner(selectFirst = true)
                }

            // 🔹 Productes
            db.collection("productes")
                .get()
                .addOnSuccessListener { result ->
                    totsProductes.clear()
                    for (doc in result) {
                        val id = doc.getString("id") ?: continue
                        val idCategoria = doc.getString("idCategoria") ?: ""
                        val nom = doc.getString("nom") ?: ""
                        val comprat = doc.getBoolean("comprat") ?: false
                        totsProductes.add(Producte(id, idCategoria, nom, comprat))
                    }
                    categoriaSeleccionadaId?.let { carregarProductesPerCategoria(it) }
                }
        }



        private fun mostrarDialogEdicioCategoria(categoria: Categoria) {
            AlertDialog.Builder(this)
                .setTitle(categoria.nom)
                .setItems(arrayOf("✏️ Editar nom", "🗑️ Eliminar categoria")) { dialog, which ->
                    when (which) {
                        0 -> editarNomCategoria(categoria)
                        1 -> confirmarEliminarCategoria(categoria)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Tancar", null)
                .show()
        }

        private fun editarNomCategoria(categoria: Categoria) {
            val input = EditText(this)
            input.setText(categoria.nom)
            input.setSelection(categoria.nom.length)

            // Configuració de marges (la repetim aquí)
            val container = FrameLayout(this)
            val density = resources.displayMetrics.density
            val margin = (16 * density).toInt()
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.leftMargin = margin
            params.rightMargin = margin
            input.layoutParams = params
            container.addView(input)

            AlertDialog.Builder(this)
                .setTitle("Editar Nom")
                .setView(container)
                .setPositiveButton("Guardar") { _, _ ->
                    val nouNom = input.text.toString().trim()
                    if (nouNom.isBlank()) {
                        Toast.makeText(this, "El nom no pot ser buit.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (nouNom != categoria.nom) {
                        categoria.nom = nouNom // Actualitzem el 'var nom' a la llista
                        setupSpinner(selectFirst = false) // Refresquem l'Spinner
                        Toast.makeText(this, "Categoria actualitzada a '$nouNom'.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel·lar", null)
                .show()
        }

        private fun confirmarEliminarCategoria(categoria: Categoria) {
            val productCount = totsProductes.count { it.idCategoria == categoria.id }

            AlertDialog.Builder(this)
                .setTitle("Confirmar Eliminació")
                .setMessage("Estàs segur que vols eliminar la categoria '${categoria.nom}'? S'eliminaran $productCount productes associats.")
                .setPositiveButton("Eliminar") { _, _ ->
                    eliminarCategoria(categoria)
                }
                .setNegativeButton("Cancel·lar", null)
                .show()
        }

        private fun eliminarCategoria(categoria: Categoria) {
            totsProductes.removeAll { it.idCategoria == categoria.id }
            totesCategories.remove(categoria)
            setupSpinner(selectFirst = true)

            // 🔥 Eliminar categoria i els productes associats
            db.collection("categories").document(categoria.id).delete()
            db.collection("productes")
                .whereEqualTo("idCategoria", categoria.id)
                .get()
                .addOnSuccessListener { result ->
                    for (doc in result) {
                        db.collection("productes").document(doc.id).delete()
                    }
                }

            Toast.makeText(this, "Categoria '${categoria.nom}' eliminada amb els seus productes.", Toast.LENGTH_SHORT).show()
        }

    }

    // --- 4. ADAPTADOR DEL RECYCLERVIEW (Sense canvis, ja preparat) ---
    class ProducteAdapter(
        private var productes: MutableList<Producte>,
        private val onCheckboxClicked: (Producte) -> Unit,
        private val onDeleteClicked: (Producte) -> Unit,
        private val onEditClicked: (Producte) -> Unit
    ) : RecyclerView.Adapter<ProducteAdapter.ProducteViewHolder>() {

        inner class ProducteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val checkboxComprat: CheckBox = itemView.findViewById(R.id.checkbox_comprat)
            val textNomProducte: TextView = itemView.findViewById(R.id.text_nom_producte)
            val buttonDelete: ImageButton = itemView.findViewById(R.id.button_delete)

            fun bind(producte: Producte) {
                textNomProducte.text = producte.nom
                checkboxComprat.isChecked = producte.comprat

                checkboxComprat.setOnCheckedChangeListener(null)
                checkboxComprat.isChecked = producte.comprat
                checkboxComprat.setOnCheckedChangeListener { _, isChecked ->
                    producte.comprat = isChecked
                    onCheckboxClicked(producte)
                }

                buttonDelete.setOnClickListener {
                    onDeleteClicked(producte)
                }

                itemView.setOnClickListener {
                    onEditClicked(producte)
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

        fun updateList(newList: MutableList<Producte>) {
            productes = newList
            notifyDataSetChanged()
        }
    }