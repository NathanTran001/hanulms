import React, { useState, useEffect, useRef } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import Layout from "../components/Layout";
import ConfirmModal from "../components/ConfirmModal";
import styles from "./styles/AdminObservationPage.module.css";
import api from "../services/apiService";
import {
	CREATE_FACULTY_PAGE,
	EDIT_FACULTY_PAGE,
	LOGIN_PAGE,
} from "../constants/paths";
import SearchBarAdmin from "../components/SearchBarAdmin";
import Pagination from "../components/Pagination";

const FacultyListPage = () => {
	const navigate = useNavigate();
	const [faculties, setFaculties] = useState([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [searchPhrase, setSearchPhrase] = useState("");
	const [deleteModal, setDeleteModal] = useState({
		isOpen: false,
		faculty: null,
	});
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(3);
	const [totalPages, setTotalPages] = useState(0);
	const debounceTimeout = useRef();

	useEffect(() => {
		fetchFaculties();
	}, [page]);

	useEffect(() => {
		if (debounceTimeout.current) clearTimeout(debounceTimeout.current);

		debounceTimeout.current = setTimeout(() => {
			if (searchPhrase.trim()) {
				handleSearch();
			} else {
				setPage(0);
				fetchFaculties();
			}
		}, 400);

		return () => clearTimeout(debounceTimeout.current);
	}, [searchPhrase]);

	const fetchFaculties = async () => {
		try {
			setLoading(true);
			const response = await api.getFaculties(page, size);
			setFaculties(response.content);
			setTotalPages(response.totalPages);
		} catch (error) {
			console.error("Error fetching faculties:", error);

			if (error.response && error.response.status === 403) {
				setError("You are not authorized to access this resource (403)");
				navigate(LOGIN_PAGE);
			} else {
				setError("Network error occurred");
			}
		} finally {
			setLoading(false);
		}
	};

	const handleSearch = async (e) => {
		if (e) e.preventDefault();
		if (!searchPhrase.trim()) {
			setPage(0);
			fetchFaculties();
			return;
		}

		try {
			setLoading(true);
			const response = await api.searchFaculties(searchPhrase, page, size);
			setFaculties(response.content);
			setTotalPages(response.totalPages);
		} catch (error) {
			console.error("Error searching faculties:", error);
			setError("Failed to search faculties. Please try again.");
		} finally {
			setLoading(false);
		}
	};

	const handleCreateFaculty = () => {
		navigate(CREATE_FACULTY_PAGE);
	};

	const handleEditFaculty = (facultyCode) => {
		navigate(EDIT_FACULTY_PAGE.replace(":facultyCode", facultyCode));
	};

	const handleDeleteClick = (faculty) => {
		setDeleteModal({
			isOpen: true,
			faculty: faculty,
		});
	};

	const handleDeleteConfirm = async () => {
		if (!deleteModal.faculty) return;

		try {
			await api.deleteFaculty(deleteModal.faculty.code);
			setDeleteModal({ isOpen: false, faculty: null });
			fetchFaculties();
		} catch (error) {
			console.error("Error deleting faculty:", error);
			setError("Failed to delete faculty. Please try again.");
		}
	};

	const handleDeleteCancel = () => {
		setDeleteModal({ isOpen: false, faculty: null });
	};

	return (
		<div className={styles.container}>
			{/* Breadcrumb */}
			<div className={styles.pathContainer}>
				<a
					href="#"
					className={styles.path}
					onClick={(e) => {
						e.preventDefault();
						navigate("/");
					}}
				>
					Home
				</a>
				<span> / </span>
				<a
					href="#"
					className={styles.path}
					onClick={(e) => e.preventDefault()}
				>
					Faculty List
				</a>
			</div>

			{/* Title */}
			<div className={styles.titleContainer}>
				<h3 className={styles.title}>FACULTY LIST</h3>
			</div>

			{/* Search and Create Section */}
			<div className={styles.actionsRow}>
				{/* <form
					onSubmit={handleSearch}
					className={styles.searchForm}
				>
					<button
						type="submit"
						className={styles.searchBtn}
					>
						<i className="bi bi-search"></i>
					</button>
					<input
						type="text"
						className={styles.searchInput}
						placeholder="Search..."
						value={searchPhrase}
						onChange={(e) => setSearchPhrase(e.target.value)}
					/>
				</form> */}
				<SearchBarAdmin
					value={searchPhrase}
					onChange={(e) => setSearchPhrase(e.target.value)}
					onSubmit={handleSearch}
				/>
				<div className={styles.createBtnContainer}>
					<button
						onClick={handleCreateFaculty}
						className={styles.createBtn}
						type="button"
					>
						Create
					</button>
				</div>
			</div>

			{/* Error Display */}
			{error && <div className={styles.error}>{error}</div>}

			{/* Faculty Table */}
			<div className={styles.tableContainer}>
				<table className={styles.table}>
					<thead>
						<tr className={styles.tableHeader}>
							<th>Code</th>
							<th>Name</th>
							<th>Lecturers</th>
							<th>Students</th>
							<th>Courses</th>
							<th>Update</th>
							<th>Delete</th>
						</tr>
					</thead>
					<tbody>
						{loading ? (
							<tr>
								<td
									colSpan="7"
									className={styles.loadingRow}
								>
									Loading faculties...
								</td>
							</tr>
						) : faculties.length === 0 ? (
							<tr>
								<td
									colSpan="7"
									className={styles.noData}
								>
									No faculties found
								</td>
							</tr>
						) : (
							faculties.map((faculty) => (
								<tr
									key={faculty.code}
									className={styles.tableRow}
								>
									<td>
										<p>{faculty.code}</p>
									</td>
									<td>
										<p>{faculty.name}</p>
									</td>
									<td>
										<p>{faculty.lecturers.length || 0}</p>
									</td>
									<td>
										<p>{faculty.students.length || 0}</p>
									</td>
									<td>
										<p>{faculty.courses.length || 0}</p>
									</td>
									<td>
										<button
											onClick={() => handleEditFaculty(faculty.code)}
											className={styles.actionBtn}
											title="Edit Faculty"
										>
											<i className="bi bi-pencil-square"></i>
										</button>
									</td>
									<td>
										<button
											onClick={() => handleDeleteClick(faculty)}
											className={`${styles.actionBtn} ${styles.deleteBtn}`}
											title="Delete Faculty"
										>
											<i className="bi bi-trash3-fill"></i>
										</button>
									</td>
								</tr>
							))
						)}
					</tbody>
				</table>
			</div>
			{!loading && faculties.length > 0 && (
				// <div className={styles.pageIndicesContainer}>
				// 	<button
				// 		onClick={() => setPage((prev) => Math.max(0, prev - 1))}
				// 		disabled={page === 0}
				// 		className={styles.paginationButton}
				// 	>
				// 		<i className="bi bi-chevron-left"></i>
				// 	</button>
				// 	<span className={styles.pageInfo}>
				// 		Page {page + 1} of {totalPages}
				// 	</span>
				// 	<button
				// 		onClick={() =>
				// 			setPage((prev) => Math.min(totalPages - 1, prev + 1))
				// 		}
				// 		disabled={page >= totalPages - 1}
				// 		className={styles.paginationButton}
				// 	>
				// 		<i className="bi bi-chevron-right"></i>
				// 	</button>
				// </div>
				<Pagination
					page={page}
					totalPages={totalPages}
					onPageChange={setPage}
				/>
			)}
			{/* Delete Confirmation Modal */}
			<ConfirmModal
				isOpen={deleteModal.isOpen}
				title="Confirm Faculty Deletion"
				message="Are you sure you want to delete this faculty along with all associated lecturers, students, and courses?"
				onConfirm={handleDeleteConfirm}
				onCancel={handleDeleteCancel}
				confirmText="Delete Faculty"
				cancelText="Cancel"
				confirmButtonClass={styles.modalDeleteBtn}
			/>
		</div>
	);
};

export default FacultyListPage;
